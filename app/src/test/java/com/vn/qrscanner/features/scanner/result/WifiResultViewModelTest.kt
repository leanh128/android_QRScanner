package com.vn.qrscanner.features.scanner.result

import android.content.Intent
import android.os.Build
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.WifiConnectResult
import com.vn.qrscanner.utils.WifiSecurityType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WifiResultViewModelTest {

    private val wifi = QrCodeType.Wifi(
        ssid = "Home",
        password = "12345678",
        securityType = WifiSecurityType.WPA,
        rawData = ""
    )

    private class FakeGateway(
        override val sdkInt: Int = Build.VERSION_CODES.P,
        var enabled: Boolean = true,
        var permissionGranted: Boolean = true,
        var validation: WifiConnectResult? = null
    ) : WifiGateway {
        override val requiredPermission = "perm"
        var pendingConnect: ((WifiConnectResult) -> Unit)? = null
        var saveResult: WifiConnectResult = WifiConnectResult.Saved

        override fun isWifiEnabled() = enabled
        override fun hasPermission(permission: String) = permissionGranted
        override fun validate(wifi: QrCodeType.Wifi) = validation
        override fun connect(wifi: QrCodeType.Wifi, onResult: (WifiConnectResult) -> Unit) {
            pendingConnect = onResult
        }
        override fun createSaveIntent(wifi: QrCodeType.Wifi) = Intent("save")
        override fun parseSaveResult(resultCode: Int, data: Intent?) = saveResult
    }

    private fun vm(gateway: FakeGateway) = WifiResultViewModel(wifi, gateway)

    private fun WifiResultViewModel.nextEvent(): WifiEvent = runBlocking { events.first() }

    @Test
    fun wifiOffOpensSettings() {
        val vm = vm(FakeGateway(enabled = false))
        assertFalse(vm.uiState.value.wifiEnabled)
        vm.onActionClick()
        assertEquals(WifiEvent.OpenWifiSettings, vm.nextEvent())
    }

    @Test
    fun refreshUpdatesButtonState() {
        val gateway = FakeGateway(enabled = false)
        val vm = vm(gateway)
        gateway.enabled = true
        vm.refreshWifiState()
        assertTrue(vm.uiState.value.wifiEnabled)
    }

    @Test
    fun missingPermissionIsRequestedOnLegacyApi() {
        val vm = vm(FakeGateway(permissionGranted = false))
        vm.onActionClick()
        assertEquals(WifiEvent.RequestPermission("perm"), vm.nextEvent())
    }

    @Test
    fun deniedPermissionWarns() {
        val vm = vm(FakeGateway())
        vm.onPermissionResult(false)
        assertEquals(WifiEvent.PermissionDenied, vm.nextEvent())
    }

    @Test
    fun legacyConnectShowsLoadingUntilResult() {
        val gateway = FakeGateway()
        val vm = vm(gateway)
        vm.onActionClick()
        assertTrue(vm.uiState.value.connecting)

        // A second tap while connecting is ignored.
        vm.onActionClick()
        gateway.pendingConnect!!(WifiConnectResult.Success)

        assertFalse(vm.uiState.value.connecting)
        assertEquals(WifiEvent.ShowResult("Home", WifiConnectResult.Success), vm.nextEvent())
    }

    @Test
    fun invalidConfigIsReportedWithoutConnecting() {
        val gateway = FakeGateway(validation = WifiConnectResult.InvalidConfig)
        val vm = vm(gateway)
        vm.onActionClick()
        assertEquals(WifiEvent.ShowResult("Home", WifiConnectResult.InvalidConfig), vm.nextEvent())
        assertFalse(vm.uiState.value.connecting)
    }

    @Test
    fun modernApiLaunchesSaveDialogWithoutPermission() {
        val vm = vm(FakeGateway(sdkInt = Build.VERSION_CODES.R, permissionGranted = false))
        vm.onActionClick()
        val event = vm.nextEvent() as WifiEvent.LaunchSaveDialog
        assertEquals("save", event.intent.action)
        assertFalse(vm.uiState.value.connecting)
    }

    @Test
    fun saveDialogResultIsForwarded() {
        val vm = vm(FakeGateway(sdkInt = Build.VERSION_CODES.R))
        vm.onSaveDialogResult(-1, null)
        assertEquals(WifiEvent.ShowResult("Home", WifiConnectResult.Saved), vm.nextEvent())
    }
}
