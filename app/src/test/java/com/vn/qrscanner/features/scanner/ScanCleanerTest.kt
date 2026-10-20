package com.vn.qrscanner.features.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScanCleanerTest {

    private fun clean(vararg texts: String?) =
        ScanCleaner.clean(texts.map { ScannedItem(it) }).map { it.text }

    @Test
    fun removesDuplicatesKeepingFirstOrder() {
        assertEquals(listOf("a1", "b2"), clean("a1", "b2", "a1", " b2 "))
    }

    @Test
    fun dropsNullBlankAndGarbled() {
        assertTrue(clean(null, "", "   ", "ab\u0000cd", "ab�cd").isEmpty())
    }

    @Test
    fun keepsLineBreaksInText() {
        assertEquals(listOf("line1\nline2"), clean("line1\nline2"))
    }

    @Test
    fun dropsSchemeOnlyCodes() {
        assertTrue(clean("http://", "https:", "mailto:").isEmpty())
        assertEquals(listOf("https://example.com"), clean("https://example.com"))
    }

    @Test
    fun dropsIncompleteWifi() {
        assertTrue(clean("WIFI:T:WPA;P:12345678;;", "WIFI:").isEmpty())
        assertEquals(
            listOf("WIFI:S:Home;T:WPA;P:12345678;;"),
            clean("WIFI:S:Home;T:WPA;P:12345678;;")
        )
    }

    private fun bmp(size: Int) =
        android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)

    @Test
    fun duplicateKeepsHighestQualityPreviewAtFirstPosition() {
        val small = bmp(10)
        val big = bmp(40)
        val out = ScanCleaner.clean(
            listOf(
                ScannedItem("a1", small),
                ScannedItem("b2", null),
                ScannedItem("a1", big),
                ScannedItem("a1", bmp(20))
            )
        )
        assertEquals(listOf("a1", "b2"), out.map { it.text })
        assertTrue(out[0].preview === big)
    }

    @Test
    fun earliestWinsQualityTie() {
        val first = bmp(10)
        val out = ScanCleaner.clean(listOf(ScannedItem("a1", first), ScannedItem("a1", bmp(10))))
        assertTrue(out.single().preview === first)
    }

    @Test
    fun collectorReplacesWithBetterPreviewOnly() {
        val collector = ScanCollector()
        val small = bmp(10)
        val big = bmp(30)
        assertTrue(collector.wouldImprove("a1", small.width * small.height))
        collector.add(ScannedItem("a1", small))
        assertTrue(!collector.wouldImprove("a1", 50))
        assertTrue(collector.wouldImprove("a1", 400))
        collector.add(ScannedItem("a1", big))
        collector.add(ScannedItem("a1", small)) // worse, ignored
        assertTrue(collector.drain().single().preview === big)
    }

    @Test
    fun collectorDedupesCapsAndDrains() {
        val collector = ScanCollector(maxItems = 2)
        collector.add(ScannedItem("a1"))
        collector.add(ScannedItem("a1"))
        collector.add(ScannedItem("b2"))
        collector.add(ScannedItem("c3")) // over the cap
        assertEquals(listOf("a1", "b2"), collector.drain().map { it.text })
        assertTrue(collector.isEmpty)
    }

    private fun clean(format: com.google.zxing.BarcodeFormat, vararg texts: String) =
        ScanCleaner.clean(texts.map { ScannedItem(it, null, format) }).map { it.text }

    @Test
    fun dropsBarcodesWithBadCheckDigitOrLength() {
        val ean13 = com.google.zxing.BarcodeFormat.EAN_13
        assertEquals(listOf("4006381333931"), clean(ean13, "4006381333931", "4006381333932", "400638133393"))
        val ean8 = com.google.zxing.BarcodeFormat.EAN_8
        assertEquals(listOf("96385074"), clean(ean8, "96385074", "96385075"))
        val upcA = com.google.zxing.BarcodeFormat.UPC_A
        assertEquals(listOf("036000291452"), clean(upcA, "036000291452", "036000291453"))
        val upcE = com.google.zxing.BarcodeFormat.UPC_E
        assertEquals(listOf("01234565"), clean(upcE, "01234565", "01234566", "2123456"))
    }

    @Test
    fun checksOtherBarcodeFormats() {
        assertEquals(
            listOf("1234"),
            clean(com.google.zxing.BarcodeFormat.ITF, "1234", "123", "12ab")
        )
        assertEquals(
            listOf("CODE-39 1"),
            clean(com.google.zxing.BarcodeFormat.CODE_39, "CODE-39 1", "code39", "A*B")
        )
        // No rule for QR or unknown formats.
        assertEquals(listOf("hello"), clean(com.google.zxing.BarcodeFormat.QR_CODE, "hello"))
    }

    @Test
    fun invalidBarcodesAreRemovedBeforeDuplicates() {
        val ean13 = com.google.zxing.BarcodeFormat.EAN_13
        val out = ScanCleaner.clean(
            listOf(
                ScannedItem("4006381333932", null, ean13), // bad
                ScannedItem("4006381333931", null, ean13),
                ScannedItem("4006381333931", null, ean13), // duplicate
                ScannedItem("4006381333932", null, ean13)  // bad again
            )
        )
        assertEquals(listOf("4006381333931"), out.map { it.text })
    }
}
