package com.vn.qrscanner.features.scanner.result

import androidx.lifecycle.SavedStateHandle
import com.vn.qrscanner.features.scanner.ScanResultItem
import com.vn.qrscanner.utils.QrCodeType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScanResultViewModelTest {

    private fun vm(content: String?) = ScanResultViewModel(
        SavedStateHandle(
            if (content == null) emptyMap() else mapOf(ScanResultViewModel.ARG_RESULT to ScanResultItem(content))
        )
    )

    @Test
    fun detectsTypeFromScannedContent() {
        assertTrue(vm("WIFI:S:Home;T:WPA;P:12345678;;").uiState.value.type is QrCodeType.Wifi)
        assertTrue(vm("https://example.com").uiState.value.type is QrCodeType.Url)
        assertTrue(vm("hello").uiState.value.type is QrCodeType.Text)
    }

    @Test
    fun urlActionOpensLink() = runBlocking {
        val vm = vm("https://example.com")
        vm.onActionClick()
        assertEquals(ScanResultEvent.OpenUrl("https://example.com"), vm.events.first())
    }

    @Test
    fun textActionSearches() = runBlocking {
        val vm = vm("hello")
        vm.onActionClick()
        assertEquals(ScanResultEvent.Search("hello"), vm.events.first())
    }

    @Test
    fun missingItemDoesNothing() = runBlocking {
        val vm = vm(null)
        vm.onActionClick()
        assertNull(withTimeoutOrNull(100) { vm.events.first() })
    }
}
