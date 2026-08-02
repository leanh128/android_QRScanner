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

    private val _scanResults = MutableStateFlow<List<Result>>(emptyList())
    val scanResults: StateFlow<List<Result>> = _scanResults

    private var decodeJob: Job? = null
    private var lastDecodeTime = 0L
    private val decodeInterval = 50L // 20fps = 1000ms / 20 = 50ms
    private var isCollecting = false

    fun decodeFrame(data: ByteArray, width: Int, height: Int, framingRect: Rect?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDecodeTime < decodeInterval || isCollecting) return
        
        lastDecodeTime = currentTime

        decodeJob?.cancel()
        decodeJob = viewModelScope.launch(Dispatchers.Default) {
            val results = BarcodeDecoder.decode(data, width, height, framingRect)
            ensureActive()
            if (results.isNotEmpty()) {
                isCollecting = true
                // Collect results for 100ms
                _scanResults.value = results
                delay(100)
                // In a real multi-code scenario, we might want to aggregate results from multiple frames here.
                // But for now, we'll just signal the results we found.
                isCollecting = false
            }
        }
    }

    fun resetResult() {
        _scanResults.value = emptyList()
        isCollecting = false
    }
}
