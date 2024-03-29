package com.vn.qrscanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
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
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.vn.qrscanner.databinding.FragmentQrGeneratorBinding
import com.vn.qrscanner.extensions.dpToPx
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnGenerate.setOnClickListener {
            if (binding.etContent.text.toString().trim().isEmpty()) {
                activity?.let { Toast.makeText(it, R.string.toast_empty_content, Toast.LENGTH_SHORT).show() }
            } else {
                val content = binding.etContent.text.toString().trim()
                if (lastGenerateText == content) {
                    hideKeyboard()
                    Handler(Looper.getMainLooper()).postDelayed({
                        (activity as? MainActivity)?.scrollToBottom()
                    }, 260)

                }else{
                    generateQR(content)
                }

            }
        }
        binding.root.setOnClickListener {
            hideKeyboard()
        }

        binding.btnClear.setOnClickListener {
            binding.etContent.setText("")
        }

//        binding.etContent.setOnClickListener {
//            (activity as? MainActivity)?.scrollToTop()
//        }

        binding.imgShare.setOnClickListener {
            binding.imgQrCode.drawable?.toBitmap()?.let {
                try {
                    val generatedFile = File(activity?.getExternalFilesDir(null), "$SHARE_DIR/$GENERATED_FILE_NAME")
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        Log.d("leon", "generated file: ${generatedFile.absolutePath}")
                        putExtra(
                            Intent.EXTRA_STREAM,
//                            FileProvider.getUriForFile(binding.root.context, "${BuildConfig.APPLICATION_ID}.file_provider", generatedFile)
                            FileProvider.getUriForFile(binding.root.context, "file_provider", generatedFile)
                        )
                        type = "image/jpeg"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Sharing QR code"))
                } catch (e: Exception) {
                    Toast.makeText(activity, "IO Error", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as? MainActivity)?.setScreenTitle(getString(R.string.title_qr_generator))
    }


    private fun hideKeyboard() {
        activity?.let {
            getSystemService(it, InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(binding.etContent.windowToken, 0)
        }
    }

    private fun generateQR(content: String) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        val generateSize = (displayMetrics.heightPixels.coerceAtLeast(200.dpToPx()).coerceAtMost(displayMetrics.widthPixels.coerceAtLeast(200.dpToPx())) *.8).toInt()
        var codeMatrix: BitMatrix? = null
        kotlin.runCatching {
            codeMatrix = QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, BITMATRIX_SIZE, BITMATRIX_SIZE, mapOf(
                    EncodeHintType.MARGIN to 1
                )
            )
        }.onFailure {
            Toast.makeText(binding.root.context, "IO Error", Toast.LENGTH_SHORT).show()
        }
        codeMatrix?.let {
            val bmp = Bitmap.createBitmap(BITMATRIX_SIZE, BITMATRIX_SIZE, Bitmap.Config.RGB_565)
            for (x in 0 until BITMATRIX_SIZE) {
                for (y in 0 until BITMATRIX_SIZE) {
                    bmp.setPixel(x, y, if (it.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            hideKeyboard()
            val displaySize = calculateQRDisplaySize()
            val displayBitmap = Bitmap.createScaledBitmap(bmp, displaySize, displaySize, false)
            writeImageToFile(displayBitmap)

            binding.imgQrCode.setImageBitmap(displayBitmap)
            Handler(Looper.getMainLooper()).postDelayed({
                (activity as? MainActivity)?.scrollToBottom()
                binding.imgShare.visibility = View.VISIBLE
            }, 200)
            lastGenerateText = content
        }
    }

    private fun calculateQRDisplaySize(): Int {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.display?.getRealMetrics(displayMetrics)
        } else {
            activity?.windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)
        }

        val width = DEFAULT_QR_DISPLAY_SIZE_DP.dpToPx().coerceAtLeast(displayMetrics.widthPixels - 32f.dpToPx())
        val height = DEFAULT_QR_DISPLAY_SIZE_DP.dpToPx().coerceAtLeast(displayMetrics.heightPixels - 32f.dpToPx())
        return (floor(minOf(width, height) / BITMATRIX_SIZE) * BITMATRIX_SIZE).toInt()
    }

    private fun writeImageToFile(bitmap: Bitmap) {
        val sharedDir = File(activity?.getExternalFilesDir(null), SHARE_DIR)
        sharedDir.mkdirs()
        val tempFile = File(sharedDir, GENERATED_FILE_NAME);
        tempFile.delete()
        val out = FileOutputStream(tempFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        out.flush()
        out.close()
    }

    override fun onStop() {
        super.onStop()
        Log.d("leon", "stop gen")
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val BITMATRIX_SIZE = 400
        private const val DEFAULT_QR_DISPLAY_SIZE_DP = 200f
        private const val GENERATED_FILE_NAME = "qr_code.jpg"
        private const val SHARE_DIR = "share"
    }
}