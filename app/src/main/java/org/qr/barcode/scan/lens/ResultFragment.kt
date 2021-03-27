package org.qr.barcode.scan.lens

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.qr.barcode.scan.lens.databinding.FragmentResultBinding
import org.qr.barcode.scan.lens.extensions.isLink

class ResultFragment : Fragment() {
    private var result: String = ""
    private var country: String = ""
    private lateinit var binding: FragmentResultBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            result = arguments!!.getString(ARG_RESULT, "")
            country = arguments!!.getString(ARG_COUNTRY, "")

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (activity as? MainActivity)?.setScreenTitle(getString(R.string.title_result))
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.layoutResult.setOnClickListener { handleResultAction() }
        binding.tvResult.text = "${if (country.isEmpty()) "" else "$country - "}$result"

        binding.btnAction.run {
            text = getString(if (result.isLink()) R.string.button_open_link else R.string.button_search)
            setOnClickListener { handleResultAction() }
        }

        binding.btnBack.run {
            setOnClickListener { backToScan() }
        }
    }

    private fun backToScan() {
        (activity as? MainActivity)?.openScanView()
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
        const val ARG_RESULT = "scan.result"
        const val ARG_CODE_TYPE = "code.type"
        const val ARG_COUNTRY = "country"
        fun newInstance(result: String?, country: String?) = ResultFragment().apply {
            arguments = bundleOf(
                    ARG_RESULT to result,
                    ARG_COUNTRY to country
            )
        }
    }
}