package org.qr.barcode.scan.lens

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.qr.barcode.scan.lens.databinding.FragmentQrGeneratorBinding
import org.qr.barcode.scan.lens.extensions.dpToPx


class QRGeneratorFragment : Fragment() {
    private lateinit var binding: FragmentQrGeneratorBinding
    private var lastGenerateText= ""
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentQrGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        (activity as? MainActivity)?.setScreenTitle(getString(R.string.title_qr_generator))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnGenerate.setOnClickListener {
            if (binding.etContent.text.toString().isEmpty()) {
                activity?.let { Toast.makeText(it, R.string.toast_empty_content, Toast.LENGTH_SHORT).show() }
            } else {
                generateQR(binding.etContent.text.toString())
            }
        }
        binding.root.setOnClickListener {
            hideKeyboard()
        }
    }


    private fun hideKeyboard() {
        activity?.let {
            getSystemService(it, InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(binding.etContent.windowToken, 0)
        }
    }

    private fun generateQR(content: String) {
        if(lastGenerateText == content)
            return
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val displayMetrics = DisplayMetrics()
        activity?.display?.getRealMetrics(displayMetrics)
        val codeSize = (displayMetrics.heightPixels.coerceAtLeast(200.dpToPx()).coerceAtMost(displayMetrics.widthPixels.coerceAtLeast(200.dpToPx())) *.75).toInt()

        Log.d("leon", "bindng.root size: ${binding.root.width} - ${binding.root.height}")
        val codeWriter = QRCodeWriter()
        val codeMatrix = codeWriter.encode(content, BarcodeFormat.QR_CODE, codeSize, codeSize)
        val bmp = Bitmap.createBitmap(codeSize, codeSize, Bitmap.Config.RGB_565)
        for (x in 0 until codeSize) {
            for (y in 0 until codeSize) {
                bmp.setPixel(x, y, if (codeMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        hideKeyboard()
        Handler(Looper.getMainLooper()).postDelayed({
            binding.imgQrCode.setImageBitmap(bmp)
            (activity as? MainActivity)?.scrollToBottom()
        },150)
        lastGenerateText = content
    }

    override fun onStop() {
        super.onStop()
        Log.d("leon", "stop gen")
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}