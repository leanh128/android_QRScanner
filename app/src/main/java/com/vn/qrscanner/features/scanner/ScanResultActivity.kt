package com.vn.qrscanner.features.scanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.ads.AdRequest
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityScanResultBinding
import com.vn.qrscanner.features.scanner.result.ResultPage
import com.vn.qrscanner.features.scanner.result.ScanResultsViewModel
import com.vn.qrscanner.features.scanner.result.TextResultFragment
import com.vn.qrscanner.features.scanner.result.UrlResultFragment
import com.vn.qrscanner.features.scanner.result.WifiResultFragment
import com.vn.qrscanner.utils.QrCodeType
import com.vn.qrscanner.utils.TempPreviewFileUtils
import kotlinx.coroutines.launch

/**
 * Hosts the toolbar, the ad, and a pager with one result fragment per scanned code, chosen by
 * the code's type. A "n / total" indicator sits on top when there is more than one result.
 */
class ScanResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.adView.loadAd(AdRequest.Builder().build())

        val viewModel = ViewModelProvider(this)[ScanResultsViewModel::class.java]
        if (viewModel.pages.isEmpty()) {
            finish()
            return
        }
        TempPreviewFileUtils.clearCache(this, viewModel.previewPaths)

        binding.pager.adapter = ResultPagerAdapter(this, viewModel.pages)
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = viewModel.onPageSelected(position)
        })

        val total = viewModel.pages.size
        binding.tvPageIndicator.visibility = if (total > 1) View.VISIBLE else View.GONE
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentIndex.collect { index ->
                    binding.tvPageIndicator.text =
                        getString(R.string.result_page_indicator, index + 1, total)
                    if (binding.pager.currentItem != index) {
                        binding.pager.setCurrentItem(index, false)
                    }
                }
            }
        }
    }

    private class ResultPagerAdapter(
        activity: FragmentActivity,
        private val pages: List<ResultPage>
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount() = pages.size

        override fun createFragment(position: Int): Fragment {
            val page = pages[position]
            return when (page.type) {
                is QrCodeType.Wifi -> WifiResultFragment.newInstance(page.item)
                is QrCodeType.Url -> UrlResultFragment.newInstance(page.item)
                is QrCodeType.Text -> TextResultFragment.newInstance(page.item)
            }
        }
    }

    companion object {
        fun showResultActivity(context: Context, items: List<ScanResultItem>) {
            val intent = Intent(context, ScanResultActivity::class.java)
            intent.putParcelableArrayListExtra(ScanResultsViewModel.ARG_RESULTS, ArrayList(items))
            context.startActivity(intent)
        }
    }
}
