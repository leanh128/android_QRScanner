package com.vn.qrscanner.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiUtilTest {

    private fun wifi(
        ssid: String = "Home",
        password: String = "",
        type: WifiSecurityType = WifiSecurityType.OPEN
    ) = QrCodeType.Wifi(ssid = ssid, password = password, securityType = type, rawData = "")

    @Test
    fun blankSsidIsInvalid() {
        assertEquals(WifiConnectResult.InvalidConfig, WifiUtil.validate(wifi(ssid = " "), true))
    }

    @Test
    fun openNetworkIsValid() {
        assertNull(WifiUtil.validate(wifi(), true))
        assertNull(WifiUtil.validate(wifi(), false))
    }

    @Test
    fun wpaRequiresPassphraseOfValidLength() {
        val wpa = WifiSecurityType.WPA
        assertEquals(
            WifiConnectResult.InvalidConfig,
            WifiUtil.validate(wifi(type = wpa), true)
        )
        assertEquals(
            WifiConnectResult.InvalidConfig,
            WifiUtil.validate(wifi(password = "short", type = wpa), true)
        )
        assertEquals(
            WifiConnectResult.InvalidConfig,
            WifiUtil.validate(wifi(password = "x".repeat(64), type = wpa), false)
        )
        assertNull(WifiUtil.validate(wifi(password = "12345678", type = wpa), true))
    }

    @Test
    fun wepOnlySupportedOnLegacyApi() {
        val wep = wifi(password = "abcde", type = WifiSecurityType.WEP)
        assertEquals(WifiConnectResult.UnsupportedSecurity, WifiUtil.validate(wep, true))
        assertNull(WifiUtil.validate(wep, false))
    }

    @Test
    fun saveDialogResultIsMapped() {
        val ok = android.app.Activity.RESULT_OK
        assertEquals(
            WifiConnectResult.Cancelled,
            WifiUtil.parseSaveNetworkResult(android.app.Activity.RESULT_CANCELED, null as List<Int>?)
        )
        assertEquals(
            WifiConnectResult.Saved,
            WifiUtil.parseSaveNetworkResult(ok, listOf(android.provider.Settings.ADD_WIFI_RESULT_SUCCESS))
        )
        assertEquals(
            WifiConnectResult.Saved,
            WifiUtil.parseSaveNetworkResult(ok, listOf(android.provider.Settings.ADD_WIFI_RESULT_ALREADY_EXISTS))
        )
        assertEquals(
            WifiConnectResult.Failed,
            WifiUtil.parseSaveNetworkResult(ok, listOf(android.provider.Settings.ADD_WIFI_RESULT_ADD_OR_UPDATE_FAILED))
        )
        assertEquals(WifiConnectResult.Failed, WifiUtil.parseSaveNetworkResult(ok, null as List<Int>?))
    }
}
