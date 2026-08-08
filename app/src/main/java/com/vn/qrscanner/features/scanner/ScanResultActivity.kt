package com.vn.qrscanner.features.scanner

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.google.android.gms.ads.AdRequest
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityScanResultBinding
import com.vn.qrscanner.extensions.isLink
import com.vn.qrscanner.extensions.toWebUri
import com.vn.qrscanner.utils.TempPreviewFileUtils

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.adView.loadAd(AdRequest.Builder().build())
        readIntentData()
        TempPreviewFileUtils.clearCache(this, resultItem?.previewPath)
        initViews()
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        resultItem?.let { item ->
            binding.tvResult.text = item.result
            item.previewPath?.let { path ->
                val bitmap = BitmapFactory.decodeFile(path)
                binding.ivPreview.setImageBitmap(bitmap)
            }
            binding.btnAction.text = getString(
                if (item.result.isLink()) R.string.button_open_link else android.R.string.search_go
            )
            binding.btnAction.setOnClickListener { handleResultAction(item.result) }
            binding.layoutResult.setOnClickListener { handleResultAction(item.result) }
        }
    }

    private fun readIntentData() {
        resultItem = IntentCompat.getParcelableExtra(intent, ARG_RESULT, ScanResultItem::class.java)
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
