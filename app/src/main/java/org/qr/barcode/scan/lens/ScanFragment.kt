package org.qr.barcode.scan.lens

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.google.zxing.ResultMetadataType
import org.qr.barcode.scan.lens.databinding.FragmentScanBinding

class ScanFragment : Fragment() {
    private lateinit var binding: FragmentScanBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (activity as? MainActivity)?.setScreenTitle(getString(R.string.title_scan))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.vScanner.run {
            setResultHandler { scanResult ->
                val country = (scanResult.resultMetadata?.get(ResultMetadataType.POSSIBLE_COUNTRY) as? String)?: ""
                (activity as? MainActivity)?.showResult(scanResult.text, country)
                stopCamera()
            }
            startCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("leon", "start scan")
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        binding.vScanner.stopCamera()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    companion object {
        private const val TAG = "ScannerView"
    }
}