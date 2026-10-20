package com.vn.qrscanner.features.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vn.qrscanner.utils.ScannerBitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.dm7.barcodescanner.zxing.BarcodeDecoder

sealed class ScanResultState {
    /** Cleaned, de-duplicated codes, in the order they were first seen. Never empty. */
    data class Success(val items: List<ScannedItem>) : ScanResultState()
    object GalleryError : ScanResultState()
}

class ScanViewModel : ViewModel() {

    private val _scanResult = MutableStateFlow<ScanResultState?>(null)
    val scanResult: StateFlow<ScanResultState?> = _scanResult

    private val collector = ScanCollector()
    private var decodeJob: Job? = null
    private var collectJob: Job? = null
    private var lastDecodeTime = 0L
    private val decodeInterval = 50L // 20fps = 1000ms / 20 = 50ms

    private var isScanningPaused = false

    /**
     * Camera frames. The first code opens a [COLLECT_WINDOW_MS] window in which the camera keeps
     * scanning; when it closes, everything found is cleaned and published as one result.
     */
    fun decodeFrame(data: ByteArray, width: Int, height: Int, framingRect: Rect?) {
        if (isScanningPaused) return
        // Skip instead of cancelling: a decode that is nearly done should not be thrown away.
        if (decodeJob?.isActive == true) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDecodeTime < decodeInterval) return
        lastDecodeTime = currentTime

        decodeJob = viewModelScope.launch(Dispatchers.Default) {
            val results = try {
                BarcodeDecoder.decodeAll(data, width, height, framingRect)
            } catch (e: Exception) {
                emptyList()
            }
            if (results.isEmpty()) return@launch

            for (result in results) {
                // Only build a preview when it is a new code or clearer than the one we hold.
                val area = ScannerBitmapUtils.previewArea(width, height, result)
                if (!collector.wouldImprove(result.text, area)) continue
                val preview = ScannerBitmapUtils.cropResultPreview(data, width, height, result)
                collector.add(ScannedItem(result.text, preview, result.barcodeFormat))
            }
            withContext(Dispatchers.Main.immediate) { startCollectWindowIfNeeded() }
        }
    }

    private fun startCollectWindowIfNeeded() {
        if (collectJob != null || isScanningPaused) return
        collectJob = viewModelScope.launch {
            delay(COLLECT_WINDOW_MS)
            finishCollecting()
        }
    }

    private fun finishCollecting() {
        collectJob = null
        pauseScanning()
        val items = collector.drain()
        if (items.isEmpty()) {
            // Everything we saw was invalid or incomplete: keep scanning as if nothing happened.
            resumeScanning()
        } else {
            _scanResult.value = ScanResultState.Success(items)
        }
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
        isScanningPaused = true
    }

    fun resumeScanning() {
        isScanningPaused = false
    }

    fun resetResult() {
        _scanResult.value = null
    }

    companion object {
        const val COLLECT_WINDOW_MS = 500L
    }
}
