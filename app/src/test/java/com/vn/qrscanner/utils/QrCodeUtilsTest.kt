package com.vn.qrscanner.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QrCodeUtilsTest {

    @Test
    fun testDetectWifiWpa() {
        val raw = "WIFI:S:MyHomeWifi;T:WPA;P:secret123;H:false;;"
        val result = QrCodeUtils.detect(raw)
        assertTrue(result is QrCodeType.Wifi)

        val wifi = result as QrCodeType.Wifi
        assertEquals("MyHomeWifi", wifi.ssid)
        assertEquals("secret123", wifi.password)
        assertEquals(WifiSecurityType.WPA, wifi.securityType)
        assertEquals(false, wifi.isHidden)
    }

    @Test
    fun testDetectWifiOpenNetwork() {
        val raw = "WIFI:S:FreePublicWifi;T:nopass;;"
        val result = QrCodeUtils.detect(raw)
        assertTrue(result is QrCodeType.Wifi)

        val wifi = result as QrCodeType.Wifi
        assertEquals("FreePublicWifi", wifi.ssid)
        assertEquals("", wifi.password)
        assertEquals(WifiSecurityType.OPEN, wifi.securityType)
    }

    @Test
    fun testDetectWifiWepHidden() {
        val raw = "WIFI:S:HiddenLegacyNet;T:WEP;P:1234567890;H:true;;"
        val result = QrCodeUtils.detect(raw)
        assertTrue(result is QrCodeType.Wifi)

        val wifi = result as QrCodeType.Wifi
        assertEquals("HiddenLegacyNet", wifi.ssid)
        assertEquals("1234567890", wifi.password)
        assertEquals(WifiSecurityType.WEP, wifi.securityType)
        assertEquals(true, wifi.isHidden)
    }

    @Test
    fun testDetectWifiEscapedCharacters() {
        val raw = "WIFI:S:My\\;Net;P:p\\:a\\;ss;T:WPA;;"
        val result = QrCodeUtils.detect(raw)
        assertTrue(result is QrCodeType.Wifi)

        val wifi = result as QrCodeType.Wifi
        assertEquals("My;Net", wifi.ssid)
        assertEquals("p:a;ss", wifi.password)
        assertEquals(WifiSecurityType.WPA, wifi.securityType)
    }

    @Test
    fun testDetectUrl() {
        val raw = "https://example.com/test"
        val result = QrCodeUtils.detect(raw)
        assertTrue(result is QrCodeType.Url)
        assertEquals("https://example.com/test", (result as QrCodeType.Url).url)
    }

    @Test
    fun testDetectText() {
        val raw = "Hello World! Just plain text."
        val result = QrCodeUtils.detect(raw)
        assertTrue(result is QrCodeType.Text)
        assertEquals("Hello World! Just plain text.", (result as QrCodeType.Text).text)
    }
}
