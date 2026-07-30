package com.vn.qrscanner.features.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.Result
import com.google.zxing.ResultMetadataType
import com.vn.qrscanner.databinding.ActivityScanBinding
import com.vn.qrscanner.features.generator.QRGeneratorActivity
import com.vn.qrscanner.utils.PermissionUtil
import kotlinx.coroutines.launch
import me.dm7.barcodescanner.core.CameraView

class ScanActivity : AppCompatActivity(), CameraView.CameraPreviewListener {
    private val viewBinding: ActivityScanBinding by lazy {
        ActivityScanBinding.inflate(
            layoutInflater
        )
    }

    private val viewModel: ScanViewModel by viewModels()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val useCaseGroup = viewBinding.scannerCamera.setupCamera(this)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    useCaseGroup
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleScanResult(rawResult: Result) {
        val country =
            (rawResult.resultMetadata?.get(ResultMetadataType.POSSIBLE_COUNTRY) as? String) ?: ""
        cameraProvider?.unbindAll()
        viewModel.resetResult()
        ScanResultActivity.showResultActivity(this@ScanActivity, rawResult.text, country)
    }

    override fun onPreviewFrame(data: ByteArray, width: Int, height: Int) {
        val framingRect = viewBinding.scannerCamera.getFramingRectInPreview(width, height)
        viewModel.decodeFrame(data, width, height, framingRect)
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
        }
    }

    private fun initView() {
        viewBinding.imgGenerateQr.setOnClickListener {
            startActivity(Intent(this, QRGeneratorActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (PermissionUtil.verifyPermission(this, Manifest.permission.CAMERA)) {
            startCamera()
        }
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val TAG = "ScanActivity"
        const val REQUEST_CODE_CAMERA = 123
    }
}
