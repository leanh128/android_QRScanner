package com.vn.qrscanner.features.scanner.result

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.vn.qrscanner.R
import com.vn.qrscanner.features.scanner.ScanResultItem
import com.vn.qrscanner.databinding.FragmentWifiResultBinding
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.WifiConnectResult
import com.vn.qrscanner.utils.WifiUtil
import com.vn.qrscanner.utils.showErrorToast
import com.vn.qrscanner.utils.showSuccessToast
import com.vn.qrscanner.utils.showWarningToast
import kotlinx.coroutines.launch

/** Result screen for `WIFI:` codes. Self-contained; decisions live in [WifiResultViewModel]. */
class WifiResultFragment : Fragment() {

    private var _binding: FragmentWifiResultBinding? = null
    private val binding get() = _binding!!

    private val resultViewModel: ScanResultViewModel by lazy {
        ViewModelProvider(this)[ScanResultViewModel::class.java]
    }

    private val viewModel: WifiResultViewModel by lazy {
        val wifi = resultViewModel.uiState.value.type as QrCodeType.Wifi
        val appContext = requireContext().applicationContext
        val factory = viewModelFactory {
            initializer { WifiResultViewModel(wifi, DefaultWifiGateway(appContext)) }
        }
        ViewModelProvider(this, factory)[WifiResultViewModel::class.java]
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    private val saveDialogLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onSaveDialogResult(result.resultCode, result.data) }

    // Only registered while the fragment is visible, so Wi-Fi is never watched in the background.
    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = viewModel.refreshWifiState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAction.setOnClickListener { viewModel.onActionClick() }
        binding.layoutResult.setOnClickListener { viewModel.onActionClick() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    resultViewModel.uiState.collect { state ->
                        binding.tvResult.text = state.result
                        binding.ivPreview.setImageBitmap(state.preview)
                    }
                }
                launch { viewModel.uiState.collect(::renderButton) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            requireContext(),
            wifiStateReceiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        viewModel.refreshWifiState()
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(wifiStateReceiver)
    }

    private fun renderButton(state: WifiUiState) {
        val button = binding.btnAction
        button.isEnabled = !state.connecting
        when {
            state.connecting -> {
                button.text = getString(R.string.button_connecting)
                button.icon = spinner()
            }
            else -> {
                button.text = getString(
                    if (state.wifiEnabled) R.string.button_connect_wifi else R.string.button_turn_on_wifi
                )
                button.setIconResource(R.drawable.ic_wifi)
            }
        }
    }

    private fun spinner(): IndeterminateDrawable<CircularProgressIndicatorSpec> {
        val spec = CircularProgressIndicatorSpec(
            requireContext(), null, 0,
            com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
        ).apply { indicatorColors = intArrayOf(binding.btnAction.currentTextColor) }
        return IndeterminateDrawable.createCircularDrawable(requireContext(), spec)
    }

    private fun handleEvent(event: WifiEvent) {
        when (event) {
            is WifiEvent.RequestPermission -> permissionLauncher.launch(event.permission)
            WifiEvent.OpenWifiSettings -> WifiUtil.openWifiSettings(requireContext())
            is WifiEvent.LaunchSaveDialog -> saveDialogLauncher.launch(event.intent)
            WifiEvent.PermissionDenied ->
                requireActivity().showWarningToast(getString(R.string.toast_wifi_permission_denied))
            is WifiEvent.ShowResult -> showResult(event.ssid, event.result)
        }
    }

    private fun showResult(ssid: String, result: WifiConnectResult) {
        val activity = requireActivity()
        when (result) {
            WifiConnectResult.Success ->
                activity.showSuccessToast(getString(R.string.toast_wifi_connected, ssid))
            WifiConnectResult.Saved ->
                activity.showSuccessToast(getString(R.string.toast_wifi_saved, ssid))
            WifiConnectResult.Failed ->
                activity.showErrorToast(getString(R.string.toast_wifi_connect_failed, ssid))
            WifiConnectResult.InvalidConfig ->
                activity.showErrorToast(getString(R.string.toast_wifi_invalid_qr))
            WifiConnectResult.UnsupportedSecurity ->
                activity.showErrorToast(getString(R.string.toast_wifi_unsupported_security))
            WifiConnectResult.Cancelled -> Unit
        }
    }

    companion object {
        fun newInstance(item: ScanResultItem) = WifiResultFragment().apply {
            arguments = bundleOf(ScanResultViewModel.ARG_RESULT to item)
        }
    }
}
