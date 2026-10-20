package com.vn.qrscanner.features.scanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.AdRequest
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityScanResultBinding
import com.vn.qrscanner.features.scanner.result.ScanResultViewModel
import com.vn.qrscanner.features.scanner.result.TextResultFragment
import com.vn.qrscanner.features.scanner.result.UrlResultFragment
import com.vn.qrscanner.features.scanner.result.WifiResultFragment
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.TempPreviewFileUtils

/**
 * Hosts the toolbar, the ad and one result fragment chosen by the type of the scanned code.
 * Everything else lives in [ScanResultViewModel] and the fragments.
 */
class ScanResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.adView.loadAd(AdRequest.Builder().build())

        val viewModel = ViewModelProvider(this)[ScanResultViewModel::class.java]
        TempPreviewFileUtils.clearCache(this, viewModel.previewPath)

        // After a configuration change the FragmentManager restores the fragment itself.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.resultContainer, fragmentFor(viewModel.uiState.value.type))
                .commit()
        }
    }

    private fun fragmentFor(type: QrCodeType): Fragment = when (type) {
        is QrCodeType.Wifi -> WifiResultFragment()
        is QrCodeType.Url -> UrlResultFragment()
        is QrCodeType.Text -> TextResultFragment()
    }

    companion object {
        fun showResultActivity(context: Context, item: ScanResultItem) {
            val intent = Intent(context, ScanResultActivity::class.java)
            intent.putExtra(ScanResultViewModel.ARG_RESULT, item)
            context.startActivity(intent)
        }
    }
}
