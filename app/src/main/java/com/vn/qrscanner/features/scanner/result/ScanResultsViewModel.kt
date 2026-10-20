package com.vn.qrscanner.features.scanner.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.vn.qrscanner.features.scanner.ScanResultItem
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.QrCodeUtils
import kotlinx.coroutines.flow.StateFlow

data class ResultPage(val item: ScanResultItem, val type: QrCodeType)

/**
 * State of the whole result screen: which pages exist and which one is showing. Each page's
 * own behaviour lives in its fragment's view model. The items arrive through the intent
 * extras, so they survive configuration changes and process death.
 */
class ScanResultsViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    val pages: List<ResultPage> =
        (savedStateHandle.get<ArrayList<ScanResultItem>>(ARG_RESULTS) ?: emptyList<ScanResultItem>())
            .map { ResultPage(it, QrCodeUtils.detect(it.result)) }

    val previewPaths: List<String> get() = pages.mapNotNull { it.item.previewPath }

    val currentIndex: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_INDEX, 0)

    fun onPageSelected(index: Int) {
        savedStateHandle[KEY_INDEX] = index
    }

    companion object {
        const val ARG_RESULTS = "scan.results"
        private const val KEY_INDEX = "scan.results.index"
    }
}
