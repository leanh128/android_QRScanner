package com.vn.qrscanner.features.scanner.result

import androidx.lifecycle.SavedStateHandle
import com.vn.qrscanner.features.scanner.ScanResultItem
import com.vn.qrscanner.utils.QrCodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScanResultsViewModelTest {

    private fun vm(items: List<ScanResultItem>?) = ScanResultsViewModel(
        SavedStateHandle(if (items == null) emptyMap() else mapOf(ScanResultsViewModel.ARG_RESULTS to ArrayList(items)))
    )

    @Test
    fun buildsOnePagePerItemInOrder() {
        val vm = vm(
            listOf(
                ScanResultItem("https://example.com", "/p1"),
                ScanResultItem("hello"),
                ScanResultItem("WIFI:S:Home;T:WPA;P:12345678;;", "/p3")
            )
        )
        assertEquals(3, vm.pages.size)
        assertTrue(vm.pages[0].type is QrCodeType.Url)
        assertTrue(vm.pages[1].type is QrCodeType.Text)
        assertTrue(vm.pages[2].type is QrCodeType.Wifi)
        assertEquals(listOf("/p1", "/p3"), vm.previewPaths)
    }

    @Test
    fun remembersCurrentPage() {
        val vm = vm(listOf(ScanResultItem("a1"), ScanResultItem("b2")))
        assertEquals(0, vm.currentIndex.value)
        vm.onPageSelected(1)
        assertEquals(1, vm.currentIndex.value)
    }

    @Test
    fun missingItemsGivesNoPages() {
        assertTrue(vm(null).pages.isEmpty())
    }
}
