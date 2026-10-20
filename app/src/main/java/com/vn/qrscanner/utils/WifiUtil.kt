package com.vn.qrscanner.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi

sealed interface WifiConnectResult {
    object Success : WifiConnectResult

    /** The system did not connect: wrong password, user cancelled, out of range, timeout... */
    object Failed : WifiConnectResult

    /** The QR payload cannot be turned into a valid network configuration. */
    object InvalidConfig : WifiConnectResult

    /** The security type cannot be used on this Android version (WEP on API 29+). */
    object UnsupportedSecurity : WifiConnectResult

    /** API 30+: the network was saved and Android will connect on its own. */
    object Saved : WifiConnectResult

    /** The user dismissed the system dialog. Not an error. */
    object Cancelled : WifiConnectResult
}

/**
 * Starts a Wi-Fi connection described by a QR code, then lets go: nothing keeps monitoring
 * the Wi-Fi state afterwards.
 *
 * - API 30+: [createSaveNetworkIntent] opens the system "save network" dialog. Android saves
 *   the network and connects by itself, so the app holds nothing. Feed the dialog's result to
 *   [parseSaveNetworkResult].
 * - API 29: [connectToWifi] uses `WifiNetworkSpecifier`, whose link only lives while its
 *   callback is registered, so the callback is kept until the next request or [disconnect].
 * - API 28: [connectToWifi] saves the network via the legacy `WifiManager`, waits until the
 *   device is on it, then unregisters everything.
 *
 * [connectToWifi]'s `onResult` is invoked on the main thread, at most once.
 */
object WifiUtil {

    private const val TAG = "WifiUtil"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val WPA_MIN_PASSPHRASE = 8
    private const val WPA_MAX_PASSPHRASE = 63
    private val WEP_HEX_LENGTHS = setOf(10, 26, 32)
    private val HEX_REGEX = Regex("[0-9a-fA-F]+")

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /** Checks if Wi-Fi is currently enabled on the device. */
    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    /**
     * Lets the user turn Wi-Fi on. On API 29+ this is the in-app settings panel, so the user
     * stays in the app; older versions open the full Wi-Fi settings page.
     */
    fun openWifiSettings(context: Context) {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_WIFI
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        val intent = Intent(action)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No Wi-Fi settings screen available", e)
        }
    }

    /** Public check for callers; null means [wifi] can be connected to on this device. */
    fun validate(wifi: QrCodeType.Wifi): WifiConnectResult? =
        validate(wifi, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)

    @RequiresApi(Build.VERSION_CODES.R)
    fun createSaveNetworkIntent(wifi: QrCodeType.Wifi): Intent {
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(wifi.ssid)
            .setIsHiddenSsid(wifi.isHidden)
            .apply {
                if (wifi.securityType == WifiSecurityType.WPA) setWpa2Passphrase(wifi.password)
            }
            .build()
        return Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(
            Settings.EXTRA_WIFI_NETWORK_LIST,
            arrayListOf(suggestion)
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun parseSaveNetworkResult(resultCode: Int, data: Intent?): WifiConnectResult =
        parseSaveNetworkResult(
            resultCode,
            data?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)
        )

    internal fun parseSaveNetworkResult(resultCode: Int, results: List<Int>?): WifiConnectResult {
        if (resultCode == Activity.RESULT_CANCELED) return WifiConnectResult.Cancelled
        return when (results?.firstOrNull()) {
            Settings.ADD_WIFI_RESULT_SUCCESS,
            Settings.ADD_WIFI_RESULT_ALREADY_EXISTS -> WifiConnectResult.Saved
            else -> WifiConnectResult.Failed
        }
    }

    @MainThread
    fun connectToWifi(
        context: Context,
        wifi: QrCodeType.Wifi,
        onResult: (WifiConnectResult) -> Unit
    ) {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        validate(wifi, modern)?.let {
            onResult(it)
            return
        }

        val appContext = context.applicationContext
        try {
            if (modern) {
                connectModern(appContext, wifi, onResult)
            } else {
                connectLegacy(appContext, wifi, onResult)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wi-Fi connect failed", e)
            onResult(WifiConnectResult.Failed)
        }
    }

    /** Releases the app-scoped connection created by [connectToWifi], if any. */
    @MainThread
    fun disconnect(context: Context) {
        val callback = activeCallback ?: return
        unregister(connectivityManager(context.applicationContext), callback)
    }

    /** Returns a failure result when [wifi] cannot be connected to, or null when it can. */
    internal fun validate(wifi: QrCodeType.Wifi, modern: Boolean): WifiConnectResult? {
        if (wifi.ssid.isBlank()) return WifiConnectResult.InvalidConfig
        return when (wifi.securityType) {
            WifiSecurityType.OPEN -> null
            WifiSecurityType.WEP ->
                if (modern) WifiConnectResult.UnsupportedSecurity else null
            WifiSecurityType.WPA ->
                if (wifi.password.length in WPA_MIN_PASSPHRASE..WPA_MAX_PASSPHRASE) {
                    null
                } else {
                    WifiConnectResult.InvalidConfig
                }
        }
    }

    // region API 29+

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectModern(
        context: Context,
        wifi: QrCodeType.Wifi,
        onResult: (WifiConnectResult) -> Unit
    ) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(wifi.ssid)
            .setIsHiddenSsid(wifi.isHidden)
            .apply {
                // WPA3-only networks are not distinguishable from the QR payload.
                if (wifi.securityType == WifiSecurityType.WPA) setWpa2Passphrase(wifi.password)
            }
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val cm = connectivityManager(context)
        activeCallback?.let { unregister(cm, it) }

        val callback = object : ConnectivityManager.NetworkCallback() {
            private var pending: ((WifiConnectResult) -> Unit)? = onResult

            // Deliver once, and drop the lambda so a long-lived callback cannot leak the caller.
            private fun deliver(result: WifiConnectResult) {
                pending?.let {
                    pending = null
                    it(result)
                }
            }

            override fun onAvailable(network: Network) = deliver(WifiConnectResult.Success)

            override fun onUnavailable() {
                deliver(WifiConnectResult.Failed)
                unregister(cm, this)
            }

            override fun onLost(network: Network) {
                deliver(WifiConnectResult.Failed)
                unregister(cm, this)
            }
        }
        activeCallback = callback
        cm.requestNetwork(request, callback, mainHandler, CONNECT_TIMEOUT_MS)
    }

    // endregion

    // region API 28

    @Suppress("DEPRECATION")
    private fun connectLegacy(
        context: Context,
        wifi: QrCodeType.Wifi,
        onResult: (WifiConnectResult) -> Unit
    ) {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }

        val config = buildLegacyConfig(wifi)
        var netId = wifiManager.addNetwork(config)
        if (netId == -1) {
            // Already saved (possibly with another password): update it instead of duplicating.
            val existing = wifiManager.configuredNetworks?.firstOrNull { it.SSID == config.SSID }
            if (existing != null) {
                config.networkId = existing.networkId
                netId = wifiManager.updateNetwork(config)
            }
        }
        if (netId == -1 || !wifiManager.enableNetwork(netId, true)) {
            onResult(WifiConnectResult.Failed)
            return
        }
        awaitLegacyConnection(context, wifiManager, wifi.ssid, onResult)
    }

    /** `enableNetwork` only queues the request, so wait until we are really on [ssid]. */
    @Suppress("DEPRECATION")
    private fun awaitLegacyConnection(
        context: Context,
        wifiManager: WifiManager,
        ssid: String,
        onResult: (WifiConnectResult) -> Unit
    ) {
        val cm = connectivityManager(context)
        activeCallback?.let { unregister(cm, it) }

        val quotedSsid = "\"$ssid\""
        var finished = false
        lateinit var callback: ConnectivityManager.NetworkCallback
        lateinit var timeout: Runnable

        fun finish(result: WifiConnectResult) {
            if (finished) return
            finished = true
            mainHandler.removeCallbacks(timeout)
            unregister(cm, callback)
            onResult(result)
        }

        fun checkConnected() {
            if (wifiManager.connectionInfo?.ssid == quotedSsid) finish(WifiConnectResult.Success)
        }

        timeout = Runnable { finish(WifiConnectResult.Failed) }
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = checkConnected()

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                checkConnected()
        }

        activeCallback = callback
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, callback, mainHandler)
        mainHandler.postDelayed(timeout, CONNECT_TIMEOUT_MS.toLong())
        checkConnected() // already on the target network
    }

    @Suppress("DEPRECATION")
    private fun buildLegacyConfig(wifi: QrCodeType.Wifi) = WifiConfiguration().apply {
        SSID = "\"${wifi.ssid}\""
        hiddenSSID = wifi.isHidden
        when (wifi.securityType) {
            WifiSecurityType.OPEN -> {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            WifiSecurityType.WEP -> {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                if (wifi.password.isNotEmpty()) {
                    val isHexKey = wifi.password.length in WEP_HEX_LENGTHS &&
                        wifi.password.matches(HEX_REGEX)
                    wepKeys[0] = if (isHexKey) wifi.password else "\"${wifi.password}\""
                    wepTxKeyIndex = 0
                }
            }
            WifiSecurityType.WPA -> {
                preSharedKey = "\"${wifi.password}\""
            }
        }
    }

    // endregion

    private fun connectivityManager(context: Context) =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun unregister(cm: ConnectivityManager, callback: ConnectivityManager.NetworkCallback) {
        if (activeCallback === callback) activeCallback = null
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
            // Already unregistered.
        }
    }
}
