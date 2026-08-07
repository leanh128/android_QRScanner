package com.vn.qrscanner.features.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.dm7.barcodescanner.zxing.BarcodeDecoder

sealed class ScanResultState {
    data class Success(val result: Result, val bitmap: Bitmap? = null) : ScanResultState()
    object GalleryError : ScanResultState()
}

class ScanViewModel : ViewModel() {

    private val _scanResult = MutableStateFlow<ScanResultState?>(null)
    val scanResult: StateFlow<ScanResultState?> = _scanResult

    private var decodeJob: Job? = null
    private var lastDecodeTime = 0L
    private val decodeInterval = 50L // 20fps = 1000ms / 20 = 50ms
    
    private var isScanningPaused = false

    fun decodeFrame(data: ByteArray, width: Int, height: Int, framingRect: Rect?) {
        if (isScanningPaused) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDecodeTime < decodeInterval) return
        
        lastDecodeTime = currentTime

        decodeJob?.cancel()
        decodeJob = viewModelScope.launch(Dispatchers.Default) {
            val result = BarcodeDecoder.decode(data, width, height, framingRect)
            if (result != null) {
                _scanResult.value = ScanResultState.Success(result)
            }
        }
    }

    fun decodeBitmap(bitmap: Bitmap) {
        pauseScanning()
        viewModelScope.launch(Dispatchers.Default) {
            val result = BarcodeDecoder.decodeBitmap(bitmap)
            if (result != null) {
                _scanResult.value = ScanResultState.Success(result, bitmap)
            } else {
                _scanResult.value = ScanResultState.GalleryError
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
}
