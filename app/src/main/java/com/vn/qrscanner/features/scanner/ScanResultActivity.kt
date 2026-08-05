package com.vn.qrscanner.features.scanner

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityScanResultBinding
import com.vn.qrscanner.extensions.isLink
import com.vn.qrscanner.extensions.toWebUri

data class ScanResultItem(
    val result: String,
    val preview: Bitmap? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readParcelable(Bitmap::class.java.classLoader)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(result)
        parcel.writeParcelable(preview, flags)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.adView.loadAd(AdRequest.Builder().build())
        readIntentData()
        initViews()
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        resultItem?.let { item ->
            binding.tvResult.text = item.result
            item.preview?.let {
                binding.ivPreview.setImageBitmap(it)
            }
            binding.btnAction.text = getString(
                if (item.result.isLink()) R.string.button_open_link else android.R.string.search_go
            )
            binding.btnAction.setOnClickListener { handleResultAction(item.result) }
            binding.layoutResult.setOnClickListener { handleResultAction(item.result) }
        }
    }

    private fun readIntentData() {
        resultItem = intent.getParcelableExtra(ARG_RESULT)
    }

    private fun handleResultAction(result: String) {
        result.takeIf { it.isNotEmpty() }
            ?.run {
                val isUrl = isLink()
                startActivity(Intent().apply {
                    action = if (isUrl) Intent.ACTION_VIEW else Intent.ACTION_WEB_SEARCH
                    if (isUrl) {
                        data = toWebUri()
                    } else {
                        putExtra(SearchManager.QUERY, result)
                    }
                })
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
