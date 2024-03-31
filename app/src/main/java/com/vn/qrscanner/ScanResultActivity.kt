package com.vn.qrscanner

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.vn.qrscanner.databinding.ActivityScanResultBinding
import com.vn.qrscanner.extensions.isLink

class ScanResultActivity : AppCompatActivity() {
    private var result: String = ""
    private var country: String = ""
    private lateinit var binding: ActivityScanResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        readIntentData()
        initViews()
    }

    private fun initViews() {
        binding.layoutResult.setOnClickListener { handleResultAction() }
        binding.tvResult.text = "${if (country.isEmpty()) "" else "$country - "}$result"
        binding.btnAction.run {
            text = getString(if (result.isLink()) R.string.button_open_link else android.R.string.search_go)
            setOnClickListener { handleResultAction() }
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun readIntentData() {
        result = intent.getStringExtra(ARG_RESULT) ?: ""
        country = intent.getStringExtra(ARG_COUNTRY) ?: ""

    }

    private fun handleResultAction() {
        result.takeIf { it.isNotEmpty() }
            ?.run {
                startActivity(Intent().apply {
                    action = if (isLink()) Intent.ACTION_VIEW else Intent.ACTION_WEB_SEARCH
                    if (isLink())
                        data = Uri.parse(binding.tvResult.text.toString())
                    else putExtra(SearchManager.QUERY, binding.tvResult.text.toString())
                })
            }
    }

    companion object {
        const val TAG = "ResultFragment"
        private const val ARG_RESULT = "scan.result"
        private const val ARG_COUNTRY = "country"

        fun showResultActivity(context: Context, result: String?, country: String?) {
            val intent = Intent(context, ScanResultActivity::class.java)
            intent.putExtras(
                bundleOf(
                    ARG_RESULT to result,
                    ARG_COUNTRY to country
                )
            )
            context.startActivity(intent)
        }
    }
}