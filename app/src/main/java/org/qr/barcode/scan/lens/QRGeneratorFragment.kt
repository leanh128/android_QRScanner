package org.qr.barcode.scan.lens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import org.qr.barcode.scan.lens.databinding.FragmentQrGeneratorBinding
import org.qr.barcode.scan.lens.extensions.dpToPx
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor


class QRGeneratorFragment : Fragment() {
    private lateinit var binding: FragmentQrGeneratorBinding
    private var lastGenerateText = ""
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

        binding.imgShare.setOnClickListener {
            binding.imgQrCode.drawable?.toBitmap()?.let { bitmap ->
                try {
                    val fileName = "qr-code.jpg"
                    val tempFile = File(it.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
                    val out = FileOutputStream(tempFile)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    out.flush()
                    out.close()
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(tempFile))
//                        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(it.context, "${BuildConfig.APPLICATION_ID}.file_provider", tempFile))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Sharing QR code"))
                } catch (e: Exception) {
                    Toast.makeText(it.context, "IO Error", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

        }
    }


    private fun hideKeyboard() {
        activity?.let {
            getSystemService(it, InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(binding.etContent.windowToken, 0)
        }
    }

    private fun generateQR(content: String) {
        if (lastGenerateText == content)
            return
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val displayMetrics = DisplayMetrics()
        activity?.display?.getRealMetrics(displayMetrics)
//        val generateSize = (displayMetrics.heightPixels.coerceAtLeast(200.dpToPx()).coerceAtMost(displayMetrics.widthPixels.coerceAtLeast(200.dpToPx())) *.8).toInt()
        val generateSize = 400
        var codeMatrix: BitMatrix? = null
        try {
            codeMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, generateSize, generateSize, mapOf(
                    EncodeHintType.MARGIN to 1
            ))
        } catch (e: WriterException) {
            Toast.makeText(binding.root.context, "IO Error", Toast.LENGTH_SHORT).show()
        }
        codeMatrix?.let {
            val bmp = Bitmap.createBitmap(generateSize, generateSize, Bitmap.Config.RGB_565)
            for (x in 0 until generateSize) {
                for (y in 0 until generateSize) {
                    bmp.setPixel(x, y, if (it.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }

            val  targetSize = (floor((displayMetrics.heightPixels.coerceAtLeast(200.dpToPx()).coerceAtMost(displayMetrics.widthPixels.coerceAtLeast(200.dpToPx())) * .9f) / generateSize) * generateSize).toInt()
            hideKeyboard()
            Handler(Looper.getMainLooper()).postDelayed({
                binding.imgQrCode.setImageBitmap(Bitmap.createScaledBitmap(bmp, targetSize, targetSize, false))
                (activity as? MainActivity)?.scrollToBottom()
                binding.imgShare.visibility = View.VISIBLE
            }, 200)
            lastGenerateText = content
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("leon", "stop gen")
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}