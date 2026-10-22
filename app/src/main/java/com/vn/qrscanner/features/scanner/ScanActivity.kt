package com.vn.qrscanner.features.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import com.vn.qrscanner.extensions.decodeBitmap
import com.vn.qrscanner.extensions.hasPermission
import com.vn.qrscanner.features.generator.QRGeneratorActivity
import com.vn.qrscanner.utils.TempPreviewFileUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.dm7.barcodescanner.core.CameraFrame
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

    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val accelerometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    /** Feeds the accelerometer to the view model, which waits for a still phone before decoding. */
    private val accelerometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, z) = event.values
            // Sensor timestamps are nanoseconds on the elapsedRealtime clock.
            viewModel.onAccelerometerSample(x, y, z, event.timestamp / 1_000_000L)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                decodeFromUri(uri)
            } else {
                viewModel.resumeScanning()
            }
        }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.resumeScanning()
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        setupInsets()
        initView()
        observeViewModel()

        if (!hasPermission(Manifest.permission.CAMERA)) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupInsets() {
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
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
        // One fresh cache for the whole batch; each preview is saved without touching the others.
        TempPreviewFileUtils.clearCache(this)
        val items = state.items.map { scanned ->
            val previewPath = scanned.preview?.let { TempPreviewFileUtils.saveBitmapToCache(this, it) }
            ScanResultItem(scanned.text.orEmpty(), previewPath)
        }

        cameraProvider?.unbindAll()
        viewModel.resetResult()
        ScanResultActivity.showResultActivity(this@ScanActivity, items)
    }


    override fun onPreviewFrame(frame: CameraFrame) {
        val framingRect = viewBinding.scannerCamera.getFramingRectInPreview(frame.width, frame.height)
        viewModel.onCameraFrame(frame, framingRect)
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
            // Frames from the other camera have nothing to do with the current attempt.
            viewModel.resumeScanning()
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
                val bitmap = uri.decodeBitmap(this@ScanActivity)
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
    }

    override fun onResume() {
        super.onResume()
        val hasSensor = accelerometer != null &&
            sensorManager.registerListener(accelerometerListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        viewModel.setMotionSensorAvailable(hasSensor)
        if (hasPermission(Manifest.permission.CAMERA)) {
            viewModel.resumeScanning()
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(accelerometerListener)
        // Leaving the screen mid-scan drops the attempt; onResume starts a fresh one.
        viewModel.pauseScanning()
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val TAG = "ScanActivity"
    }
}
