package com.vn.qrscanner

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.google.zxing.ResultMetadataType
import com.vn.qrscanner.databinding.FragmentScanBinding
import me.dm7.barcodescanner.zxing.ZXingScannerView.ResultHandler

class ScanFragment : Fragment() {
    private lateinit var binding: FragmentScanBinding

    private val scanResultHandler by lazy {
        ResultHandler { scanResult ->
            if (context == null) return@ResultHandler
            val country = (scanResult.resultMetadata?.get(ResultMetadataType.POSSIBLE_COUNTRY) as? String) ?: ""
            ScanResultActivity.showResultActivity(requireContext(), scanResult.text, country)
            binding.vScanner.stopCamera()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as? MainActivity)?.setScreenTitle(getString(R.string.title_scan))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding.vScanner) {
            setResultHandler(scanResultHandler)
        }
    }

    override fun onStart() {
        super.onStart()
        binding.vScanner.startCamera()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        binding.vScanner.stopCamera()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}