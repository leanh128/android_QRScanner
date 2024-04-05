package com.vn.qrscanner.ui.screen

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.google.android.gms.ads.AdRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.vn.qrscanner.BuildConfig
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityQrGeneratorBinding
import com.vn.qrscanner.extensions.android.scrollToBottom
import com.vn.qrscanner.extensions.dpToPx
import java.io.File
import java.io.FileOutputStream
import kotlin.math.floor


class QRGeneratorActivity : AppCompatActivity() {
    private val binding by lazy { ActivityQrGeneratorBinding.inflate(layoutInflater) }
    private var lastGenerateText = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        initViews()
        binding.adView.loadAd(AdRequest.Builder().build())
    }

    private fun initViews() {
        binding.btnGenerate.setOnClickListener {
            val content = binding.etContent.text.toString().trim().takeIf { it.isNotEmpty() } ?: let {
                Toast.makeText(this, R.string.toast_empty_content, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (lastGenerateText == content) {
                hideKeyboard()
            } else {
                generateQR(content)
            }
        }
        binding.root.setOnClickListener { hideKeyboard() }

        binding.btnClear.setOnClickListener { binding.etContent.text = null }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.imgShare.setOnClickListener {
            try {
                val generatedFile = File(getExternalFilesDir(null), "$SHARE_DIR/$GENERATED_FILE_NAME")
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    Log.d("leon", "generated file: ${generatedFile.absolutePath}")
                    putExtra(
                        Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(
                            this@QRGeneratorActivity,
                            "${BuildConfig.APPLICATION_ID}.file_provider",
                            generatedFile
                        )
                    )
                    type = "image/jpeg"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Sharing QR code"))
            } catch (e: Exception) {
                Toast.makeText(this, "IO Error", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun hideKeyboard() {
        getSystemService(this, InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.etContent.windowToken, 0)
    }

    private fun generateQR(content: String) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val codeMatrix = kotlin.runCatching {
            QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, BITMATRIX_SIZE, BITMATRIX_SIZE, mapOf(
                    EncodeHintType.MARGIN to 1
                )
            )
        }.onFailure {
            Toast.makeText(binding.root.context, "IO Error", Toast.LENGTH_SHORT).show()
        }.getOrNull() ?: return
        val bmp = Bitmap.createBitmap(BITMATRIX_SIZE, BITMATRIX_SIZE, Bitmap.Config.RGB_565)
        for (x in 0 until BITMATRIX_SIZE) {
            for (y in 0 until BITMATRIX_SIZE) {
                bmp.setPixel(x, y, if (codeMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }

        hideKeyboard()
        lastGenerateText = content
        val displayBitmap = Bitmap.createScaledBitmap(bmp, 300.dpToPx(), 300.dpToPx(), false)
        binding.imgQRCode.setImageBitmap(displayBitmap)
        writeImageToFile(displayBitmap)

        binding.imgShare.isVisible = true
        binding.imgQRCode.isVisible = true
        Handler(mainLooper).postDelayed({
            binding.contentContainer.scrollToBottom()
        }, 200)

    }

    private fun calculateQRDisplaySize(): Int {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.getRealMetrics(displayMetrics)
        } else {
            windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)
        }

        val width = DEFAULT_QR_DISPLAY_SIZE_DP.dpToPx().coerceAtLeast(displayMetrics.widthPixels - 32f.dpToPx())
        val height = DEFAULT_QR_DISPLAY_SIZE_DP.dpToPx().coerceAtLeast(displayMetrics.heightPixels - 32f.dpToPx())
        return (floor(minOf(width, height) / BITMATRIX_SIZE) * BITMATRIX_SIZE).toInt()
    }

    private fun writeImageToFile(bitmap: Bitmap) {
        val sharedDir = File(getExternalFilesDir(null), SHARE_DIR)
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
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val BITMATRIX_SIZE = 400
        private const val DEFAULT_QR_DISPLAY_SIZE_DP = 200f
        private const val GENERATED_FILE_NAME = "qr_code.jpg"
        private const val SHARE_DIR = "share"
    }
}