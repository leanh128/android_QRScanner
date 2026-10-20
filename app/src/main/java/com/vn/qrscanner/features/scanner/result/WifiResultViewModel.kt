package com.vn.qrscanner.features.scanner.result

import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.WifiConnectResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

data class WifiUiState(
    val wifiEnabled: Boolean,
    val connecting: Boolean = false
)

/** One-shot actions the fragment performs; the view model decides when they happen. */
sealed interface WifiEvent {
    data class RequestPermission(val permission: String) : WifiEvent
    object OpenWifiSettings : WifiEvent
    data class LaunchSaveDialog(val intent: Intent) : WifiEvent
    data class ShowResult(val ssid: String, val result: WifiConnectResult) : WifiEvent
    object PermissionDenied : WifiEvent
}

/**
 * Decides what the Wi-Fi action button does: turn Wi-Fi on, ask for permission, show the
 * system save dialog (API 30+) or connect in-app (API 28-29), and tracks the loading state.
 * It only starts a connection and reports the outcome; it does not watch Wi-Fi afterwards.
 */
class WifiResultViewModel(
    private val wifi: QrCodeType.Wifi,
    private val gateway: WifiGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiUiState(wifiEnabled = gateway.isWifiEnabled()))
    val uiState: StateFlow<WifiUiState> = _uiState.asStateFlow()

    private val _events = Channel<WifiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Call when the screen resumes or the system reports a Wi-Fi state change. */
    fun refreshWifiState() {
        val enabled = gateway.isWifiEnabled()
        _uiState.update { it.copy(wifiEnabled = enabled) }
    }

    fun onActionClick() {
        if (_uiState.value.connecting) return
        refreshWifiState()
        when {
            !_uiState.value.wifiEnabled -> send(WifiEvent.OpenWifiSettings)
            // The API 30+ save dialog needs no runtime permission.
            gateway.sdkInt >= Build.VERSION_CODES.R -> connect()
            gateway.hasPermission(gateway.requiredPermission) -> connect()
            else -> send(WifiEvent.RequestPermission(gateway.requiredPermission))
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            send(WifiEvent.PermissionDenied)
            return
        }
        refreshWifiState()
        if (_uiState.value.wifiEnabled) connect() else send(WifiEvent.OpenWifiSettings)
    }

    fun onSaveDialogResult(resultCode: Int, data: Intent?) {
        send(WifiEvent.ShowResult(wifi.ssid, gateway.parseSaveResult(resultCode, data)))
    }

    private fun connect() {
        gateway.validate(wifi)?.let {
            send(WifiEvent.ShowResult(wifi.ssid, it))
            return
        }
        if (gateway.sdkInt >= Build.VERSION_CODES.R) {
            send(WifiEvent.LaunchSaveDialog(gateway.createSaveIntent(wifi)))
            return
        }
        _uiState.update { it.copy(connecting = true) }
        gateway.connect(wifi) { result ->
            _uiState.update { it.copy(connecting = false) }
            send(WifiEvent.ShowResult(wifi.ssid, result))
        }
    }

    private fun send(event: WifiEvent) {
        _events.trySend(event)
    }
}
