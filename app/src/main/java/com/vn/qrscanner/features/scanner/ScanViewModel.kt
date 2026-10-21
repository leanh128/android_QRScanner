package com.vn.qrscanner.features.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.vn.qrscanner.utils.ScannerBitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.dm7.barcodescanner.core.CameraFrame
import me.dm7.barcodescanner.zxing.BarcodeDecoder

sealed class ScanResultState {
    /** Cleaned, de-duplicated codes, in the order they were first seen. Never empty. */
    data class Success(val items: List<ScannedItem>) : ScanResultState()
    object GalleryError : ScanResultState()
}

class ScanViewModel : ViewModel() {

    private val _scanResult = MutableStateFlow<ScanResultState?>(null)
    val scanResult: StateFlow<ScanResultState?> = _scanResult

    /**
     * Camera scanning goes through these phases:
     * - [Phase.WAITING_FOR_STABLE]: nothing is decoded until the camera is held still.
     * - [Phase.SCANNING]: frames are decoded one at a time, at most [DECODE_INTERVAL_MS] apart.
     * - [Phase.COLLECTING]: a frame held a valid code; for [COLLECT_WINDOW_MS] the next frames
     *   are only stored. Moving the camera cancels and starts over.
     * - [Phase.PROCESSING]: the stored frames are decoded and the result is published.
     */
    private enum class Phase { WAITING_FOR_STABLE, SCANNING, COLLECTING, PROCESSING }

    private class StoredFrame(val data: ByteArray, val width: Int, val height: Int, val framingRect: Rect?)

    private sealed interface FrameAction {
        object Skip : FrameAction
        data class Decode(val session: Int) : FrameAction
        data class Store(val session: Int) : FrameAction
    }

    // Frames arrive on the camera thread, pause/resume on the main thread, and decode results on
    // background coroutines: every field below is only touched while holding this lock.
    private val lock = Any()
    private val stability = CameraStabilityDetector()
    private val collector = ScanCollector()
    private val storedFrames = ArrayList<StoredFrame>()
    private var phase = Phase.WAITING_FOR_STABLE
    private var isScanningPaused = false

    /** Bumped whenever scanning starts over, so work from an abandoned attempt is ignored. */
    private var session = 0
    private var decodeJob: Job? = null
    private var collectJob: Job? = null
    private var lastDecodeTime = 0L
    private var lastStoreTime = 0L
    private var anchorGrid: FloatArray? = null

    /** Called on the camera thread for every frame; [frame] is only valid during this call. */
    fun onCameraFrame(frame: CameraFrame, framingRect: Rect?) {
        if (synchronized(lock) { isScanningPaused || phase == Phase.PROCESSING }) return

        val now = SystemClock.elapsedRealtime()
        val gridStart = System.nanoTime()
        val grid = frame.lumaGrid()
        val gridTime = ScanLog.ms(gridStart)
        val action = synchronized(lock) { nextAction(grid, now, gridTime) }
        if (action == FrameAction.Skip) return

        // The full copy is the expensive part: only frames we actually use pay for it.
        val copyStart = System.nanoTime()
        val data = try {
            frame.toNv21()
        } catch (e: Exception) {
            Log.w(ScanLog.TAG, "Failed to read camera frame", e)
            synchronized(lock) { if (phase == Phase.COLLECTING) startOver("frame read failed") }
            return
        }
        ScanLog.d { "Frame ${frame.width}x${frame.height} copied in ${ScanLog.ms(copyStart)} for $action" }
        val stored = StoredFrame(data, frame.width, frame.height, framingRect)
        synchronized(lock) {
            when (action) {
                is FrameAction.Decode ->
                    if (action.session == session) decodeJob = launchDecode(stored, grid, session)
                is FrameAction.Store ->
                    if (action.session == session && phase == Phase.COLLECTING) {
                        storedFrames.add(stored)
                        ScanLog.d { "Stored frame ${storedFrames.size}/$MAX_STORED_FRAMES" }
                    }
                FrameAction.Skip -> {}
            }
        }
    }

    /** Decides what to do with a frame from its brightness grid. Call with [lock] held. */
    private fun nextAction(grid: FloatArray, now: Long, gridTime: String): FrameAction {
        if (isScanningPaused) return FrameAction.Skip
        val isStable = stability.update(grid, now)
        val anchor = anchorGrid
        val drift = if (phase == Phase.COLLECTING && anchor != null) stability.driftFrom(anchor, grid) else Float.NaN
        val phaseBefore = phase
        val action = when (phase) {
            Phase.WAITING_FOR_STABLE -> {
                if (isStable) {
                    phase = Phase.SCANNING
                    ScanLog.d { "Camera stable (still ${stability.stillForMs(now)}ms): start decoding" }
                    decodeIfDue(now)
                } else {
                    FrameAction.Skip
                }
            }
            Phase.SCANNING -> {
                if (isStable) {
                    decodeIfDue(now)
                } else {
                    startOver("camera moved while scanning (change ${ScanLog.f(stability.smoothedChange)})")
                    FrameAction.Skip
                }
            }
            Phase.COLLECTING -> when {
                !isStable -> {
                    startOver("camera moved while collecting (change ${ScanLog.f(stability.smoothedChange)})")
                    FrameAction.Skip
                }
                anchor == null || stability.hasDrifted(anchor, grid) -> {
                    startOver("view drifted while collecting (drift ${ScanLog.f(drift)})")
                    FrameAction.Skip
                }
                now - lastStoreTime < DECODE_INTERVAL_MS || storedFrames.size >= MAX_STORED_FRAMES ->
                    FrameAction.Skip
                else -> {
                    lastStoreTime = now
                    FrameAction.Store(session)
                }
            }
            Phase.PROCESSING -> FrameAction.Skip
        }
        ScanLog.frame {
            "$phaseBefore change=${ScanLog.f(stability.lastChange)} " +
                "smoothed=${ScanLog.f(stability.smoothedChange)} " +
                "still=${stability.stillForMs(now)}ms drift=${ScanLog.f(drift)} " +
                "grid=$gridTime -> ${action.javaClass.simpleName}"
        }
        return action
    }

    private fun decodeIfDue(now: Long): FrameAction {
        // Skip instead of cancelling: a decode that is nearly done should not be thrown away.
        if (decodeJob?.isActive == true || now - lastDecodeTime < DECODE_INTERVAL_MS) {
            return FrameAction.Skip
        }
        lastDecodeTime = now
        return FrameAction.Decode(session)
    }

    private fun launchDecode(frame: StoredFrame, grid: FloatArray, decodeSession: Int): Job =
        viewModelScope.launch(Dispatchers.Default) {
            val items = decodeValid(frame, "Scan decode") { true }
            if (items.isEmpty()) return@launch
            synchronized(lock) {
                if (decodeSession != session || phase != Phase.SCANNING || isScanningPaused) {
                    ScanLog.d { "Dropped scan decode result: scanning started over meanwhile" }
                    return@launch
                }
                startCollecting(items, grid)
            }
        }

    /** A frame held at least one valid code: keep the camera's next frames for a moment. */
    private fun startCollecting(firstItems: List<ScannedItem>, grid: FloatArray) {
        phase = Phase.COLLECTING
        anchorGrid = grid
        storedFrames.clear()
        lastStoreTime = 0L
        collector.clear()
        firstItems.forEach(collector::add)
        ScanLog.d { "Found ${firstItems.size} valid code(s): collecting frames for ${COLLECT_WINDOW_MS}ms" }
        val collectSession = session
        collectJob = viewModelScope.launch(Dispatchers.Default) {
            delay(COLLECT_WINDOW_MS)
            processStoredFrames(collectSession)
        }
    }

    private suspend fun processStoredFrames(collectSession: Int) {
        val frames = synchronized(lock) {
            if (collectSession != session || phase != Phase.COLLECTING) return
            phase = Phase.PROCESSING
            storedFrames.toList().also { storedFrames.clear() }
        }
        ScanLog.d { "Collect window over: decoding ${frames.size} stored frame(s)" }
        val processStart = System.nanoTime()

        // Frames are decoded in parallel. A late add after a cancel is harmless: the collector is
        // cleared when the next collect window starts.
        coroutineScope {
            frames.map { frame ->
                async {
                    // Only build a preview when it is a new code or clearer than the one we hold.
                    decodeValid(frame, "Stored frame decode") { result ->
                        collector.wouldImprove(
                            result.text,
                            ScannerBitmapUtils.previewArea(frame.width, frame.height, result)
                        )
                    }.forEach(collector::add)
                }
            }.awaitAll()
        }

        synchronized(lock) {
            if (collectSession != session) return
            collectJob = null
            val items = collector.drain()
            ScanLog.d {
                "Processed ${frames.size} frame(s) in ${ScanLog.ms(processStart)}: " +
                    "${items.size} code(s) ${items.map { describe(it.text, it.format) }}"
            }
            if (items.isEmpty()) {
                startOver("no valid code after cleaning")
            } else {
                isScanningPaused = true
                _scanResult.value = ScanResultState.Success(items)
            }
        }
    }

    /**
     * Decodes [frame] and returns its valid codes (barcodes must fit their format), with a preview
     * for each one [wantsPreview] accepts.
     */
    private suspend fun decodeValid(
        frame: StoredFrame,
        label: String,
        wantsPreview: (Result) -> Boolean
    ): List<ScannedItem> {
        val decodeStart = System.nanoTime()
        val results = try {
            BarcodeDecoder.decodeAll(frame.data, frame.width, frame.height, frame.framingRect)
        } catch (e: Exception) {
            Log.w(ScanLog.TAG, "$label failed", e)
            emptyList()
        }
        val (valid, invalid) = results.partition { result ->
            val text = result.text?.trim()
            text != null && ScanCleaner.isValid(text, result.barcodeFormat)
        }
        ScanLog.d {
            "$label in ${ScanLog.ms(decodeStart)}: ${results.size} found, " +
                "valid ${valid.map { describe(it.text, it.barcodeFormat) }}, " +
                "invalid ${invalid.map { describe(it.text, it.barcodeFormat) }}"
        }
        return valid
            .map { result ->
                val preview = if (wantsPreview(result)) {
                    ScannerBitmapUtils.cropResultPreview(frame.data, frame.width, frame.height, result)
                } else {
                    null
                }
                ScannedItem(result.text, preview, result.barcodeFormat)
            }
    }

    /**
     * Drops everything from the current attempt and waits for the camera to settle again.
     * [reason] is only logged.
     */
    private fun startOver(reason: String) {
        if (phase != Phase.WAITING_FOR_STABLE) ScanLog.d { "Start over from $phase: $reason" }
        session++
        phase = Phase.WAITING_FOR_STABLE
        decodeJob?.cancel()
        decodeJob = null
        collectJob?.cancel()
        collectJob = null
        storedFrames.clear()
        collector.clear()
        anchorGrid = null
        stability.reset()
    }

    fun decodeBitmap(bitmap: Bitmap) {
        pauseScanning()
        viewModelScope.launch(Dispatchers.Default) {
            val results = try {
                BarcodeDecoder.decodeBitmapAll(bitmap)
            } catch (e: Exception) {
                emptyList()
            }
            val items = ScanCleaner.clean(
                results.map { result ->
                    ScannedItem(
                        result.text,
                        ScannerBitmapUtils.cropBitmap(bitmap, result),
                        result.barcodeFormat
                    )
                }
            )
            _scanResult.value = if (items.isEmpty()) {
                ScanResultState.GalleryError
            } else {
                ScanResultState.Success(items)
            }
        }
    }

    fun pauseScanning() {
        synchronized(lock) {
            isScanningPaused = true
            startOver("scanning paused")
        }
    }

    /** Starts camera scanning afresh: the camera has to settle before anything is decoded. */
    fun resumeScanning() {
        synchronized(lock) {
            isScanningPaused = false
            startOver("scanning resumed")
        }
    }

    private fun describe(text: String?, format: BarcodeFormat?): String =
        "$format:" + (text?.let { if (it.length > 40) it.take(40) + "…" else it } ?: "null")

    fun resetResult() {
        _scanResult.value = null
    }

    companion object {
        /** At most 20 frames per second are decoded, or stored while collecting. */
        const val DECODE_INTERVAL_MS = 50L
        const val COLLECT_WINDOW_MS = 500L

        /** One collect window at [DECODE_INTERVAL_MS] needs no more; caps memory on big frames. */
        const val MAX_STORED_FRAMES = (COLLECT_WINDOW_MS / DECODE_INTERVAL_MS).toInt()
    }
}
