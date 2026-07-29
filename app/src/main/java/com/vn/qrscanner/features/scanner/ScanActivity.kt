package com.vn.qrscanner.features.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.vn.qrscanner.databinding.ActivityScanBinding
import com.vn.qrscanner.features.generator.QRGeneratorActivity
import com.vn.qrscanner.utils.PermissionUtil
import me.dm7.barcodescanner.core.CameraView
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity(), CameraView.CameraPreviewListener {
    private val viewBinding: ActivityScanBinding by lazy {
        ActivityScanBinding.inflate(
            layoutInflater
        )
    }
    
    private val viewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PermissionUtil.verifyPermission(this, Manifest.permission.CAMERA)) {
            PermissionUtil.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA
            )
        } else {
            initView()
        }
        
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scanResult.collect { result ->
                    if (result != null) {
                        handleScanResult(result)
                    }
                }
            }
        }
    }

    private fun handleScanResult(rawResult: Result) {
        val country = (rawResult.resultMetadata?.get(ResultMetadataType.POSSIBLE_COUNTRY) as? String) ?: ""
        ScanResultActivity.showResultActivity(this@ScanActivity, rawResult.text, country)
        viewBinding.scannerCamera.stopCamera()
        viewModel.resetResult()
    }

    override fun onPreviewFrame(data: ByteArray, width: Int, height: Int, camera: Camera) {
        val framingRect = viewBinding.scannerCamera.getFramingRectInPreview(width, height)
        viewModel.decodeFrame(data, width, height, framingRect)
        
        // Request next frame if no result yet
        if (viewModel.scanResult.value == null) {
            camera.setOneShotPreviewCallback(viewBinding.scannerCamera)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
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
        viewBinding.scannerCamera.setCameraPreviewListener(this)
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
