package com.vn.qrscanner.features.scanner.result

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vn.qrscanner.features.scanner.ScanResultItem
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.QrCodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanResultUiState(
    val result: String,
    val type: QrCodeType,
    val preview: Bitmap? = null
)

/** One-shot actions the view has to perform (they need a Context). */
sealed interface ScanResultEvent {
    data class OpenUrl(val url: String) : ScanResultEvent
    data class Search(val text: String) : ScanResultEvent
}

/**
 * State shared by the activity and whichever result fragment is showing. The scanned item
 * arrives through the activity's intent extras (via [SavedStateHandle]), so it survives
 * configuration changes and process death.
 */
class ScanResultViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val item: ScanResultItem =
        savedStateHandle.get<ScanResultItem>(ARG_RESULT) ?: ScanResultItem("")

    private val _uiState = MutableStateFlow(
        ScanResultUiState(result = item.result, type = QrCodeUtils.detect(item.result))
    )
    val uiState: StateFlow<ScanResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<ScanResultEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val previewPath: String? get() = item.previewPath

    init {
        item.previewPath?.let { path ->
            viewModelScope.launch {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
                _uiState.update { it.copy(preview = bitmap) }
            }
        }
    }

    /** Primary action for URL and plain-text results; Wi-Fi has its own view model. */
    fun onActionClick() {
        if (item.result.isEmpty()) return
        when (val type = _uiState.value.type) {
            is QrCodeType.Url -> _events.trySend(ScanResultEvent.OpenUrl(type.url))
            is QrCodeType.Text -> _events.trySend(ScanResultEvent.Search(type.text))
            is QrCodeType.Wifi -> Unit
        }
    }

    companion object {
        const val ARG_RESULT = "scan.result"
    }
}
