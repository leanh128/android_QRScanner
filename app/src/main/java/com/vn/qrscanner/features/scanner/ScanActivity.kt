package com.vn.qrscanner.features.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityScanBinding
import com.vn.qrscanner.features.generator.QRGeneratorActivity
import com.vn.qrscanner.utils.BitmapUtils
import com.vn.qrscanner.utils.TempPreviewFileUtils
import com.vn.qrscanner.utils.PermissionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.dm7.barcodescanner.core.CameraView
import java.util.concurrent.TimeUnit

class ScanActivity : AppCompatActivity(), CameraView.CameraPreviewListener {
    private val viewBinding: ActivityScanBinding by lazy {
        ActivityScanBinding.inflate(layoutInflater)
    }

    private val viewModel: ScanViewModel by viewModels()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashOn = false
    private var lastPreviewFrame: ByteArray? = null
    private var lastFrameWidth = 0
    private var lastFrameHeight = 0

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                decodeFromUri(uri)
            } else {
                viewModel.resumeScanning()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setupFullScreen()

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

    private fun setupFullScreen() {
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            statusBarColor = android.graphics.Color.TRANSPARENT
        }

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.toolbar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.footer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scanResult.collect { state ->
                    when (state) {
                        is ScanResultState.Success -> {
                            handleScanResult(state)
                        }
                        is ScanResultState.GalleryError -> {
                            Toast.makeText(
                                this@ScanActivity,
                                "No QR code found in the image",
                                Toast.LENGTH_SHORT
                            ).show()
                            viewModel.resumeScanning()
                            viewModel.resetResult()
                        }
                        null -> {}
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

                // Set up CameraView with appropriate ratio and offset
                viewBinding.scannerCamera.setTargetAspectRatio(AspectRatio.RATIO_4_3)
                val offset32dp = (32 * resources.displayMetrics.density).toInt()
                viewBinding.scannerCamera.setCustomEdgeOffset(offset32dp)

                val useCaseGroup = viewBinding.scannerCamera.setupCamera(this)

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    useCaseGroup
                )

                updateCameraFeaturesUI()

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateCameraFeaturesUI() {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
        viewBinding.btnFlash.isEnabled = hasFlash
        viewBinding.btnFlash.alpha = if (hasFlash) 1.0f else 0.3f

        // Reset flash state if not supported
        if (!hasFlash && isFlashOn) {
            isFlashOn = false
            viewBinding.btnFlash.setImageResource(R.drawable.ic_flash_off)
        } else if (hasFlash) {
            camera?.cameraControl?.enableTorch(isFlashOn)
        }
    }

    private fun handleScanResult(state: ScanResultState.Success) {
        val previewBitmap = if (state.bitmap != null) {
            BitmapUtils.cropBitmap(state.bitmap, state.result)
        } else {
            lastPreviewFrame?.let { data ->
                BitmapUtils.cropResultPreview(data, lastFrameWidth, lastFrameHeight, state.result)
            }
        }
        
        val previewPath = previewBitmap?.let { 
            TempPreviewFileUtils.saveBitmapToCache(this, it)
        }
        val resultItem = ScanResultItem(state.result.text, previewPath)

        cameraProvider?.unbindAll()
        viewModel.resetResult()
        ScanResultActivity.showResultActivity(this@ScanActivity, resultItem)
    }


    override fun onPreviewFrame(data: ByteArray, width: Int, height: Int) {
        lastPreviewFrame = data
        lastFrameWidth = width
        lastFrameHeight = height
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
        viewBinding.btnCreateQr.setOnClickListener {
            startActivity(Intent(this, QRGeneratorActivity::class.java))
        }

        viewBinding.btnSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        viewBinding.btnFlash.setOnClickListener {
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                isFlashOn = !isFlashOn
                viewBinding.btnFlash.setImageResource(if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
                camera?.cameraControl?.enableTorch(isFlashOn)
            }
        }

        viewBinding.btnGallery.setOnClickListener {
            viewModel.pauseScanning()
            galleryLauncher.launch("image/*")
        }

        setupCameraGestures()
    }

    private fun setupCameraGestures() {
        val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                camera?.cameraControl?.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(this, scaleListener)

        val tapListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val factory = viewBinding.scannerCamera.getPreviewView().meteringPointFactory
                val point = factory.createPoint(e.x, e.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                camera?.cameraControl?.startFocusAndMetering(action)
                return true
            }
        }
        val gestureDetector = GestureDetector(this, tapListener)

        viewBinding.scannerCamera.getPreviewView().setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            true
        }
    }

    private fun decodeFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapUtils.decodeBitmapFromUri(this@ScanActivity, uri)
                    ?: throw Exception("Failed to decode bitmap")
                viewModel.decodeBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode image from gallery", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ScanActivity,
                        "Failed to load image",
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.resumeScanning()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (PermissionUtil.verifyPermission(this, Manifest.permission.CAMERA)) {
            viewModel.resumeScanning()
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
