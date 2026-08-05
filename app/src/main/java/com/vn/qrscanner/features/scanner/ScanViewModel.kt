package com.vn.qrscanner.features.scanner

import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.Result
import kotlinx.coroutines.Dispatchers
import me.dm7.barcodescanner.zxing.BarcodeDecoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScanViewModel : ViewModel() {

    private val _scanResult = MutableStateFlow<Result?>(null)
    val scanResult: StateFlow<Result?> = _scanResult

    private var decodeJob: Job? = null
    private var lastDecodeTime = 0L
    private val decodeInterval = 50L // 20fps = 1000ms / 20 = 50ms

    fun decodeFrame(data: ByteArray, width: Int, height: Int, framingRect: Rect?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDecodeTime < decodeInterval) return
        
        lastDecodeTime = currentTime

        decodeJob?.cancel()
        decodeJob = viewModelScope.launch(Dispatchers.Default) {
            val result = BarcodeDecoder.decode(data, width, height, framingRect)
            ensureActive()
            if (result != null) {
                _scanResult.value = result
            }
        }
    }

    fun resetResult() {
        _scanResult.value = null
    }
}
