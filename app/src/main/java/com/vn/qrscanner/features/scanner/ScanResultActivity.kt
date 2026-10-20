package com.vn.qrscanner.features.scanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityScanResultBinding
import com.vn.qrscanner.extensions.hasPermission
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.QrCodeUtils
import com.vn.qrscanner.utils.TempPreviewFileUtils
import com.vn.qrscanner.utils.WifiConnectResult
import com.vn.qrscanner.utils.WifiUtil
import com.vn.qrscanner.utils.showErrorToast
import com.vn.qrscanner.utils.showInfoToast
import com.vn.qrscanner.utils.showSuccessToast
import com.vn.qrscanner.utils.showWarningToast

data class ScanResultItem(
    val result: String,
    val previewPath: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(result)
        parcel.writeString(previewPath)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ScanResultItem> {
        override fun createFromParcel(parcel: Parcel): ScanResultItem {
            return ScanResultItem(parcel)
        }

        override fun newArray(size: Int): Array<ScanResultItem?> {
            return arrayOfNulls(size)
        }
    }
}

class ScanResultActivity : AppCompatActivity() {
    private var resultItem: ScanResultItem? = null
    private lateinit var binding: ActivityScanResultBinding
    private var qrCodeType: QrCodeType? = null
    private var pendingWifiInfo: QrCodeType.Wifi? = null

    private val requestWifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val wifi = pendingWifiInfo
        pendingWifiInfo = null
        if (isGranted) {
            if (wifi != null) {
                if (WifiUtil.isWifiEnabled(this)) {
                    connectWifi(wifi)
                } else {
                    updateWifiButtonState()
                    WifiUtil.openWifiSettings(this)
                }
            }
        } else {
            showWarningToast(getString(R.string.toast_wifi_permission_denied))
        }
    }

    private var pendingSaveWifi: QrCodeType.Wifi? = null
    private var isConnectingWifi = false

    private val saveWifiLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val wifi = pendingSaveWifi ?: return@registerForActivityResult
        pendingSaveWifi = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showWifiResult(wifi, WifiUtil.parseSaveNetworkResult(result.resultCode, result.data))
        }
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isConnectingWifi) updateWifiButtonState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.adView.loadAd(AdRequest.Builder().build())
        readIntentData()
        TempPreviewFileUtils.clearCache(this, resultItem?.previewPath)
        initViews()
    }

    override fun onStart() {
        super.onStart()
        // Only while visible: keeps the button in sync if Wi-Fi is toggled from the settings
        // panel or quick settings. Nothing is observed once the screen is hidden.
        if (qrCodeType is QrCodeType.Wifi) {
            ContextCompat.registerReceiver(
                this,
                wifiStateReceiver,
                IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (qrCodeType is QrCodeType.Wifi) unregisterReceiver(wifiStateReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (qrCodeType is QrCodeType.Wifi && !isConnectingWifi) {
            updateWifiButtonState()
        }
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        resultItem?.let { item ->
            binding.tvResult.text = item.result
            item.previewPath?.let { path ->
                val bitmap = BitmapFactory.decodeFile(path)
                binding.ivPreview.setImageBitmap(bitmap)
            }

            val codeType = QrCodeUtils.detect(item.result)
            this.qrCodeType = codeType

            when (codeType) {
                is QrCodeType.Wifi -> {
                    updateWifiButtonState()
                }
                is QrCodeType.Url -> {
                    binding.btnAction.text = getString(R.string.button_open_link)
                    binding.btnAction.icon = null
                }
                is QrCodeType.Text -> {
                    binding.btnAction.text = getString(android.R.string.search_go)
                    binding.btnAction.icon = null
                }
            }

            binding.btnAction.setOnClickListener { handleResultAction(item.result) }
            binding.layoutResult.setOnClickListener { handleResultAction(item.result) }
        }
    }

    private fun updateWifiButtonState() {
        if (qrCodeType !is QrCodeType.Wifi) return
        if (WifiUtil.isWifiEnabled(this)) {
            binding.btnAction.text = getString(R.string.button_connect_wifi)
        } else {
            binding.btnAction.text = getString(R.string.button_turn_on_wifi)
        }
        binding.btnAction.setIconResource(R.drawable.ic_wifi)
    }

    private fun readIntentData() {
        resultItem = IntentCompat.getParcelableExtra(intent, ARG_RESULT, ScanResultItem::class.java)
    }

    private fun handleResultAction(result: String) {
        if (result.isEmpty()) return
        when (val type = qrCodeType ?: QrCodeUtils.detect(result)) {
            is QrCodeType.Wifi -> {
                if (WifiUtil.isWifiEnabled(this)) {
                    checkWifiPermissionAndConnect(type)
                } else {
                    WifiUtil.openWifiSettings(this)
                }
            }
            is QrCodeType.Url -> {
                QrCodeUtils.openUrl(this, type.url)
            }
            is QrCodeType.Text -> {
                QrCodeUtils.searchWeb(this, type.text)
            }
        }
    }

    private fun getRequiredWifiPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
    }

    private fun checkWifiPermissionAndConnect(wifi: QrCodeType.Wifi) {
        // The API 30+ "save network" dialog needs no runtime permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            connectWifi(wifi)
            return
        }
        val permission = getRequiredWifiPermission()
        if (hasPermission(permission)) {
            connectWifi(wifi)
        } else {
            pendingWifiInfo = wifi
            requestWifiPermissionLauncher.launch(permission)
        }
    }

    private fun connectWifi(wifi: QrCodeType.Wifi) {
        if (isConnectingWifi) return
        WifiUtil.validate(wifi)?.let {
            showWifiResult(wifi, it)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingSaveWifi = wifi
            saveWifiLauncher.launch(WifiUtil.createSaveNetworkIntent(wifi))
            return
        }

        setWifiConnecting(true)
        WifiUtil.connectToWifi(this, wifi) { result ->
            setWifiConnecting(false)
            showWifiResult(wifi, result)
        }
    }

    private fun showWifiResult(wifi: QrCodeType.Wifi, result: WifiConnectResult) {
        when (result) {
            WifiConnectResult.Success ->
                showSuccessToast(getString(R.string.toast_wifi_connected, wifi.ssid))
            WifiConnectResult.Saved ->
                showSuccessToast(getString(R.string.toast_wifi_saved, wifi.ssid))
            WifiConnectResult.Failed ->
                showErrorToast(getString(R.string.toast_wifi_connect_failed, wifi.ssid))
            WifiConnectResult.InvalidConfig ->
                showErrorToast(getString(R.string.toast_wifi_invalid_qr))
            WifiConnectResult.UnsupportedSecurity ->
                showErrorToast(getString(R.string.toast_wifi_unsupported_security))
            WifiConnectResult.Cancelled -> Unit
        }
    }

    /** Swaps the action button for a disabled "Connecting…" state with a spinner. */
    private fun setWifiConnecting(connecting: Boolean) {
        isConnectingWifi = connecting
        val button = binding.btnAction
        button.isEnabled = !connecting
        if (connecting) {
            button.text = getString(R.string.button_connecting)
            val spec = CircularProgressIndicatorSpec(
                this, null, 0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
            ).apply { indicatorColors = intArrayOf(button.currentTextColor) }
            button.icon = IndeterminateDrawable.createCircularDrawable(this, spec)
        } else {
            updateWifiButtonState()
        }
    }

    companion object {
        private const val ARG_RESULT = "scan.result"

        fun showResultActivity(context: Context, item: ScanResultItem) {
            val intent = Intent(context, ScanResultActivity::class.java)
            intent.putExtra(ARG_RESULT, item)
            context.startActivity(intent)
        }
    }
}
