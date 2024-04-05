package com.vn.qrscanner.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.google.zxing.ResultMetadataType
import com.vn.qrscanner.databinding.ActivityScanBinding
import com.vn.qrscanner.utils.PermissionUtil
import me.dm7.barcodescanner.zxing.ZXingScannerView

class ScanActivity : AppCompatActivity() {
    private val viewBinding: ActivityScanBinding by lazy { ActivityScanBinding.inflate(layoutInflater) }
    private val scanResultHandler by lazy {
        ZXingScannerView.ResultHandler { scanResult ->
            val country = (scanResult.resultMetadata?.get(ResultMetadataType.POSSIBLE_COUNTRY) as? String) ?: ""
            ScanResultActivity.showResultActivity(this, scanResult.text, country)
            viewBinding.scannerCamera.stopCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PermissionUtil.verifyPermission(this, Manifest.permission.CAMERA)) {
            PermissionUtil.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA)
        } else {
            initView()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CAMERA) {
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    finish()
                    return
                }
            }
            initView()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                viewBinding.scannerCamera.startCamera()
        }
    }

    private fun initView() {
        setContentView(viewBinding.root)
        viewBinding.imgGenerateQr.setOnClickListener {
            startActivity(Intent(this, QRGeneratorActivity::class.java))
        }
        viewBinding.scannerCamera.setAspectTolerance(0.5f) //for Huewei devices
        viewBinding.scannerCamera.setResultHandler(scanResultHandler)
    }

    override fun onStart() {
        super.onStart()
        viewBinding.scannerCamera.startCamera()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStop() {
        super.onStop()
        viewBinding.scannerCamera.stopCamera()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val TAG = "MainActivity"
        const val REQUEST_CODE_CAMERA = 123
    }
}