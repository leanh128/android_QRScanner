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

    init {
        if (ScanLog.enabled) BarcodeDecoder.debugLog = { message -> ScanLog.d { message } }
    }

    private val _scanResult = MutableStateFlow<ScanResultState?>(null)
    val scanResult: StateFlow<ScanResultState?> = _scanResult

    /**
     * Camera scanning goes through these phases:
     * - [Phase.WAITING_FOR_STABLE]: nothing is decoded until the phone is held still.
     * - [Phase.SCANNING]: frames are decoded one at a time, at most [DECODE_INTERVAL_MS] apart.
     * - [Phase.COLLECTING]: a frame held a valid code; for [COLLECT_WINDOW_MS] the next frames
     *   are only stored. Moving the phone cancels and starts over.
     * - [Phase.PROCESSING]: the stored frames are decoded, and everything read (raw: text, format,
     *   location, preview) is cleaned (invalid codes dropped, duplicates removed) and published.
     *
     * In [Phase.SCANNING] and [Phase.COLLECTING] a frame is only used if the scan area in it is
     * at least [MIN_SHARPNESS] sharp; blurry frames are ignored.
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
    private val stability = MotionStabilityDetector()
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

    /** Without an accelerometer there is nothing to wait for: the camera counts as stable. */
    private var hasMotionSensor = true

    /** Called with every accelerometer sample (any thread); [timeMs] is on the elapsedRealtime clock. */
    fun onAccelerometerSample(x: Float, y: Float, z: Float, timeMs: Long) {
        synchronized(lock) { stability.update(x, y, z, timeMs) }
    }

    /** Tells whether the device has an accelerometer to judge stability with. */
    fun setMotionSensorAvailable(available: Boolean) {
        synchronized(lock) { hasMotionSensor = available }
    }

    /** Called on the camera thread for every frame; [frame] is only valid during this call. */
    fun onCameraFrame(frame: CameraFrame, framingRect: Rect?) {
        if (synchronized(lock) { isScanningPaused || phase == Phase.PROCESSING }) return

        val now = SystemClock.elapsedRealtime()
        val action = synchronized(lock) { nextAction(now) }
        if (action == FrameAction.Skip) return

        // Cheap compared to the copy below, and a blurry frame is not worth decoding.
        val sharpnessStart = System.nanoTime()
        val sharpness = frame.sharpness(framingRect)
        if (sharpness < MIN_SHARPNESS) {
            ScanLog.frame { "Blurry frame ignored: sharpness=${ScanLog.f(sharpness)} (${ScanLog.ms(sharpnessStart)})" }
            return
        }
        ScanLog.frame { "Sharp frame: sharpness=${ScanLog.f(sharpness)} (${ScanLog.ms(sharpnessStart)})" }
        synchronized(lock) {
            if (action is FrameAction.Decode) lastDecodeTime = now else lastStoreTime = now
        }

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
                    if (action.session == session) decodeJob = launchDecode(stored, session)
                is FrameAction.Store ->
                    if (action.session == session && phase == Phase.COLLECTING) {
                        storedFrames.add(stored)
                        ScanLog.d { "Stored frame ${storedFrames.size}/$MAX_STORED_FRAMES" }
                    }
                FrameAction.Skip -> {}
            }
        }
    }

    /** Decides what to do with a frame that arrived at [now]. Call with [lock] held. */
    private fun nextAction(now: Long): FrameAction {
        if (isScanningPaused) return FrameAction.Skip
        val isStable = !hasMotionSensor || stability.isStable(now)
        val phaseBefore = phase
        val action = when (phase) {
            Phase.WAITING_FOR_STABLE -> {
                if (isStable) {
                    phase = Phase.SCANNING
                    ScanLog.d { "Phone stable (still ${stability.stillForMs(now)}ms): start decoding" }
                    decodeIfDue(now)
                } else {
                    FrameAction.Skip
                }
            }
            Phase.SCANNING -> {
                if (isStable) {
                    decodeIfDue(now)
                } else {
                    startOver("phone moved while scanning (change ${ScanLog.f(stability.smoothedChange)})")
                    FrameAction.Skip
                }
            }
            Phase.COLLECTING -> when {
                !isStable -> {
                    startOver("phone moved while collecting (change ${ScanLog.f(stability.smoothedChange)})")
                    FrameAction.Skip
                }
                now - lastStoreTime < STORE_INTERVAL_MS || storedFrames.size >= MAX_STORED_FRAMES ->
                    FrameAction.Skip
                else -> FrameAction.Store(session)
            }
            Phase.PROCESSING -> FrameAction.Skip
        }
        ScanLog.frame {
            "$phaseBefore change=${ScanLog.f(stability.lastChange)} " +
                "smoothed=${ScanLog.f(stability.smoothedChange)} " +
                "still=${stability.stillForMs(now)}ms -> ${action.javaClass.simpleName}"
        }
        return action
    }

    private fun decodeIfDue(now: Long): FrameAction {
        // Skip instead of cancelling: a decode that is nearly done should not be thrown away.
        if (decodeJob?.isActive == true || now - lastDecodeTime < DECODE_INTERVAL_MS) {
            return FrameAction.Skip
        }
        return FrameAction.Decode(session)
    }

    private fun launchDecode(frame: StoredFrame, decodeSession: Int): Job =
        viewModelScope.launch(Dispatchers.Default) {
            val items = decodeRaw(frame, "Scan decode") { true }
            // Only a frame with a usable code opens a collect window.
            if (ScanCleaner.removeInvalid(items).isEmpty()) return@launch
            synchronized(lock) {
                if (decodeSession != session || phase != Phase.SCANNING || isScanningPaused) {
                    ScanLog.d { "Dropped scan decode result: scanning started over meanwhile" }
                    return@launch
                }
                startCollecting(items)
            }
        }

    /** A frame held at least one valid code: keep the camera's next frames for a moment. */
    private fun startCollecting(firstItems: List<ScannedItem>) {
        phase = Phase.COLLECTING
        storedFrames.clear()
        lastStoreTime = 0L
        collector.clear()
        firstItems.forEach(collector::add)
        ScanLog.d { "Found ${firstItems.size} code(s): collecting frames for ${COLLECT_WINDOW_MS}ms" }
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
                    decodeRaw(frame, "Stored frame decode") { result ->
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
     * Decodes [frame] and returns everything it read, raw: text, format and location of each code,
     * valid or not. Only valid ones get a preview, and only if [wantsPreview] accepts them.
     */
    private suspend fun decodeRaw(
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
        ScanLog.d {
            "$label in ${ScanLog.ms(decodeStart)}: ${results.size} found " +
                results.map { describe(it.text, it.barcodeFormat) }
        }
        return results.map { result ->
            val text = result.text?.trim()
            val isValid = text != null && ScanCleaner.isValid(text, result.barcodeFormat)
            val preview = if (isValid && wantsPreview(result)) {
                ScannerBitmapUtils.cropResultPreview(frame.data, frame.width, frame.height, result)
            } else {
                null
            }
            ScannedItem(result.text, preview, result.barcodeFormat, ScannerBitmapUtils.resultBounds(result))
        }
    }

    /**
     * Drops everything from the current attempt and waits for the phone to settle again.
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

    /** Starts camera scanning afresh: the phone has to settle before anything is decoded. */
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
        /** At most 20 frames per second are decoded. */
        const val DECODE_INTERVAL_MS = 50L

        /** After a code is found, frames are stored for this long. */
        const val COLLECT_WINDOW_MS = 100L

        /** Frames are stored about as fast as the camera delivers them (30 fps). */
        const val STORE_INTERVAL_MS = 30L

        /** One collect window at [STORE_INTERVAL_MS] needs no more; caps memory on big frames. */
        const val MAX_STORED_FRAMES = (COLLECT_WINDOW_MS / STORE_INTERVAL_MS).toInt() + 1

        /**
         * Smallest [CameraFrame.sharpness] of the scan area for a frame to be used. A code held
         * in focus scores in the hundreds or more and a clearly blurry view scores a few tens, so
         * this lets through slightly soft frames that a code can still be read from. Not tuned on
         * devices: watch the `QrScanFrame` log and adjust.
         */
        const val MIN_SHARPNESS = 50f
    }
}
