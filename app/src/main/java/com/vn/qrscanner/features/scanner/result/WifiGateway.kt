package com.vn.qrscanner.features.scanner.result

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.vn.qrscanner.extensions.hasPermission
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.WifiConnectResult
import com.vn.qrscanner.utils.WifiUtil

/** Everything [WifiResultViewModel] needs from the platform, so it can be unit tested. */
interface WifiGateway {
    val sdkInt: Int

    /** Runtime permission needed before [connect] on API < 30. */
    val requiredPermission: String

    fun isWifiEnabled(): Boolean
    fun hasPermission(permission: String): Boolean
    fun validate(wifi: QrCodeType.Wifi): WifiConnectResult?
    fun connect(wifi: QrCodeType.Wifi, onResult: (WifiConnectResult) -> Unit)
    fun createSaveIntent(wifi: QrCodeType.Wifi): Intent
    fun parseSaveResult(resultCode: Int, data: Intent?): WifiConnectResult
}

class DefaultWifiGateway(private val context: Context) : WifiGateway {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT

    override val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

    override fun isWifiEnabled() = WifiUtil.isWifiEnabled(context)

    override fun hasPermission(permission: String) = context.hasPermission(permission)

    override fun validate(wifi: QrCodeType.Wifi) = WifiUtil.validate(wifi)

    override fun connect(wifi: QrCodeType.Wifi, onResult: (WifiConnectResult) -> Unit) =
        WifiUtil.connectToWifi(context, wifi, onResult)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun createSaveIntent(wifi: QrCodeType.Wifi) = WifiUtil.createSaveNetworkIntent(wifi)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun parseSaveResult(resultCode: Int, data: Intent?) =
        WifiUtil.parseSaveNetworkResult(resultCode, data)
}
