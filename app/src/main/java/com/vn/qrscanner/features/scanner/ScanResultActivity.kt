package com.vn.qrscanner.features.scanner

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityScanResultBinding
import com.vn.qrscanner.databinding.ItemScanResultBinding
import com.vn.qrscanner.extensions.isLink
import androidx.core.net.toUri

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
    private var results: List<ScanResultItem> = emptyList()
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
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = ScanResultAdapter(results) { item ->
            handleResultAction(item.result)
        }
    }

    private fun readIntentData() {
        results = intent.getParcelableArrayListExtra<ScanResultItem>(ARG_RESULTS) ?: emptyList()
    }

    private fun handleResultAction(result: String) {
        result.takeIf { it.isNotEmpty() }
            ?.run {
                startActivity(Intent().apply {
                    action = if (isLink()) Intent.ACTION_VIEW else Intent.ACTION_WEB_SEARCH
                    if (isLink())
                        data = result.toUri()
                    else putExtra(SearchManager.QUERY, result)
                })
            }
    }

    companion object {
        private const val ARG_RESULTS = "scan.results"

        fun showResultActivity(context: Context, items: ArrayList<ScanResultItem>) {
            val intent = Intent(context, ScanResultActivity::class.java)
            intent.putParcelableArrayListExtra(ARG_RESULTS, items)
            context.startActivity(intent)
        }
    }

    class ScanResultAdapter(
        private val items: List<ScanResultItem>,
        private val onAction: (ScanResultItem) -> Unit
    ) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemScanResultBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemScanResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvResult.text = item.result
            item.preview?.let {
                holder.binding.ivPreview.setImageBitmap(it)
            }
            holder.binding.btnAction.text = holder.itemView.context.getString(
                if (item.result.isLink()) R.string.button_open_link else android.R.string.search_go
            )
            holder.binding.btnAction.setOnClickListener { onAction(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
