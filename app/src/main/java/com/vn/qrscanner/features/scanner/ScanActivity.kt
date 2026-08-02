package com.vn.qrscanner.features.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
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
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.ActivityScanBinding
import com.vn.qrscanner.features.generator.QRGeneratorActivity
import com.vn.qrscanner.utils.PermissionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.dm7.barcodescanner.core.CameraView
import java.io.ByteArrayOutputStream
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

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { decodeFromUri(it) }
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
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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
                viewModel.scanResults.collect { results ->
                    if (results.isNotEmpty()) {
                        handleScanResults(results)
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

    private fun handleScanResults(results: List<Result>) {
        val resultItems = ArrayList<ScanResultItem>()
        
        for (result in results) {
            val previewBitmap = lastPreviewFrame?.let { data ->
                cropResultPreview(data, lastFrameWidth, lastFrameHeight, result)
            }
            resultItems.add(ScanResultItem(result.text, previewBitmap))
        }

        cameraProvider?.unbindAll()
        viewModel.resetResult()
        ScanResultActivity.showResultActivity(this@ScanActivity, resultItems)
    }

    private fun cropResultPreview(data: ByteArray, width: Int, height: Int, result: Result): Bitmap? {
        try {
            val points = result.resultPoints ?: return null
            if (points.size < 2) return null

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            for (point in points) {
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }

            // Calculate width and height of the code area
            val codeWidth = maxX - minX
            val codeHeight = maxY - minY
            
            // Adjust to fit screen width (full width preview)
            // We want the code to be centered and have some context around it
            val paddingFactor = 1.5f
            val cropWidth = (codeWidth * paddingFactor).toInt()
            val cropHeight = (cropWidth * 9 / 16) // 16:9 aspect ratio or fixed height in UI
            
            val centerX = (minX + maxX) / 2
            val centerY = (minY + maxY) / 2
            
            val rect = Rect(
                maxOf(0, (centerX - cropWidth / 2).toInt()),
                maxOf(0, (centerY - cropHeight / 2).toInt()),
                minOf(width, (centerX + cropWidth / 2).toInt()),
                minOf(height, (centerY + cropHeight / 2).toInt())
            )

            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(rect, 90, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop preview", e)
            return null
        }
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
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream) ?: throw Exception("Failed to decode bitmap")
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                
                val source = RGBLuminanceSource(width, height, pixels)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val reader = MultiFormatReader().apply {
                    val hints = HashMap<DecodeHintType, Any>()
                    hints[DecodeHintType.TRY_HARDER] = true
                    hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(
                        BarcodeFormat.QR_CODE,
                        BarcodeFormat.DATA_MATRIX,
                        BarcodeFormat.AZTEC,
                        BarcodeFormat.PDF_417,
                        BarcodeFormat.CODE_128,
                        BarcodeFormat.CODE_39,
                        BarcodeFormat.EAN_13,
                        BarcodeFormat.EAN_8
                    )
                    setHints(hints)
                }
                
                val result = try {
                    reader.decode(binaryBitmap)
                } catch (e: Exception) {
                    val invertedSource = source.invert()
                    val invertedBinaryBitmap = BinaryBitmap(HybridBinarizer(invertedSource))
                    reader.decode(invertedBinaryBitmap)
                }

                withContext(Dispatchers.Main) {
                    handleScanResults(listOf(result))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode image from gallery", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScanActivity, "No QR code found in the image", Toast.LENGTH_SHORT).show()
                }
            }
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
