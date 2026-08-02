package me.dm7.barcodescanner.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import me.dm7.barcodescanner.R

open class CameraView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr) {

    interface CameraPreviewListener {
        fun onPreviewFrame(data: ByteArray, width: Int, height: Int)
    }

    private var mPreviewView: PreviewView = PreviewView(context)
    private var mViewFinderView: ViewFinderView? = null

    private var mFlashState: Boolean = false
    private var mIsLaserEnabled = true

    @ColorInt
    private var mLaserColor = ContextCompat.getColor(context, R.color.viewfinder_laser)

    @ColorInt
    private var mBorderColor = ContextCompat.getColor(context, R.color.viewfinder_border)
    private var mMaskColor = ContextCompat.getColor(context, R.color.viewfinder_mask)
    private var mBorderWidth = resources.getInteger(R.integer.viewfinder_border_width)
    private var mBorderLength = resources.getInteger(R.integer.viewfinder_border_length)
    private var mRoundedCorner = false
    private var mCornerRadius = 0
    private var mSquaredFinder = true
    private var mBorderAlpha = 1.0f
    private var mViewFinderOffset = 0
    private var mTargetAspectRatio: Int = AspectRatio.RATIO_4_3

    init {
        val a = context.theme.obtainStyledAttributes(
            attributeSet,
            R.styleable.BarcodeScannerView,
            0, 0
        )

        try {
            mIsLaserEnabled =
                a.getBoolean(R.styleable.BarcodeScannerView_laserEnabled, mIsLaserEnabled)
            mLaserColor = a.getColor(R.styleable.BarcodeScannerView_laserColor, mLaserColor)
            mBorderColor = a.getColor(R.styleable.BarcodeScannerView_borderColor, mBorderColor)
            mMaskColor = a.getColor(R.styleable.BarcodeScannerView_maskColor, mMaskColor)
            mBorderWidth =
                a.getDimensionPixelSize(R.styleable.BarcodeScannerView_borderWidth, mBorderWidth)
            mBorderLength =
                a.getDimensionPixelSize(R.styleable.BarcodeScannerView_borderLength, mBorderLength)
            mRoundedCorner =
                a.getBoolean(R.styleable.BarcodeScannerView_roundedCorner, mRoundedCorner)
            mCornerRadius =
                a.getDimensionPixelSize(R.styleable.BarcodeScannerView_cornerRadius, mCornerRadius)
            mSquaredFinder =
                a.getBoolean(R.styleable.BarcodeScannerView_squaredFinder, mSquaredFinder)
            mBorderAlpha = a.getFloat(R.styleable.BarcodeScannerView_borderAlpha, mBorderAlpha)
            mViewFinderOffset = a.getDimensionPixelSize(
                R.styleable.BarcodeScannerView_finderOffset,
                mViewFinderOffset
            )
        } finally {
            a.recycle()
        }

        addView(mPreviewView)
        mViewFinderView = createViewFinderView(context)
        addView(mViewFinderView)
    }

    protected fun createViewFinderView(context: Context): ViewFinderView {
        return ViewFinderView(context).apply {
            setBorderColor(mBorderColor)
            setLaserColor(mLaserColor)
            setLaserEnabled(mIsLaserEnabled)
            setBorderStrokeWidth(mBorderWidth)
            setBorderLineLength(mBorderLength)
            setMaskColor(mMaskColor)
            setBorderCornerRounded(mRoundedCorner)
            setBorderCornerRadius(mCornerRadius)
            setSquareViewFinder(mSquaredFinder)
            setViewFinderOffset(mViewFinderOffset)
        }
    }

    fun setupCamera(listener: CameraPreviewListener): UseCaseGroup {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    mTargetAspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build().also {
                it.surfaceProvider = mPreviewView.surfaceProvider
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(resolutionSelector)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    processImageProxy(imageProxy, listener)
                }
            }

        val builder = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageAnalysis)

        mPreviewView.viewPort?.let {
            builder.setViewPort(it)
        }

        return builder.build()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy, listener: CameraPreviewListener) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height

        val nv21 = yuv420888ToNv21(imageProxy)

        val rotatedData = when (rotationDegrees) {
            90 -> rotateNv2190(nv21, width, height)
            180 -> rotateNv21180(nv21, width, height)
            270 -> rotateNv21270(nv21, width, height)
            else -> nv21
        }

        val finalWidth = if (rotationDegrees % 180 != 0) height else width
        val finalHeight = if (rotationDegrees % 180 != 0) width else height

        listener.onPreviewFrame(rotatedData, finalWidth, finalHeight)
        imageProxy.close()
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val pixelCount = image.width * image.height
        val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
        val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
        val imagePlanes = image.planes

        // Y plane
        val yPlane = imagePlanes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val width = image.width
        val height = image.height

        var offset = 0
        for (y in 0 until height) {
            yBuffer.position(y * yRowStride)
            for (x in 0 until width) {
                outputBuffer[offset++] = yBuffer.get()
                if (yPixelStride > 1) {
                    yBuffer.position(yBuffer.position() + yPixelStride - 1)
                }
            }
        }

        // UV planes (interleaved for NV21: V, U, V, U...)
        val vPlane = imagePlanes[2]
        val uPlane = imagePlanes[1]
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        for (y in 0 until height / 2) {
            for (x in 0 until width / 2) {
                val vIndex = y * vRowStride + x * vPixelStride
                val uIndex = y * uRowStride + x * uPixelStride
                outputBuffer[offset++] = vBuffer.get(vIndex)
                outputBuffer[offset++] = uBuffer.get(uIndex)
            }
        }

        return outputBuffer
    }

    private fun rotateNv2190(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val rotated = ByteArray(data.size)
        var k = 0
        for (i in 0 until width) {
            for (j in height - 1 downTo 0) {
                rotated[k++] = data[j * width + i]
            }
        }
        k = ySize
        for (i in 0 until width step 2) {
            for (j in height / 2 - 1 downTo 0) {
                rotated[k++] = data[ySize + j * width + i]
                rotated[k++] = data[ySize + j * width + i + 1]
            }
        }
        return rotated
    }

    private fun rotateNv21180(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val rotated = ByteArray(data.size)
        for (i in 0 until ySize) {
            rotated[ySize - i - 1] = data[i]
        }
        for (i in 0 until ySize / 2 step 2) {
            rotated[ySize + ySize / 2 - i - 2] = data[ySize + i]
            rotated[ySize + ySize / 2 - i - 1] = data[ySize + i + 1]
        }
        return rotated
    }

    private fun rotateNv21270(data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val rotated = ByteArray(data.size)
        var k = 0
        for (i in width - 1 downTo 0) {
            for (j in 0 until height) {
                rotated[k++] = data[j * width + i]
            }
        }
        k = ySize
        for (i in width - 1 downTo 0 step 2) {
            for (j in 0 until height / 2) {
                rotated[k++] = data[ySize + j * width + i - 1]
                rotated[k++] = data[ySize + j * width + i]
            }
        }
        return rotated
    }

    fun setFlash(flag: Boolean) {
        mFlashState = flag
    }

    fun setTargetAspectRatio(ratio: Int) {
        mTargetAspectRatio = ratio
    }

    fun setCustomEdgeOffset(offset: Int) {
        mViewFinderView?.setCustomEdgeOffset(offset)
    }

    fun getPreviewView(): PreviewView = mPreviewView

    fun getFramingRectInPreview(previewWidth: Int, previewHeight: Int): Rect? {
        val framingRect = mViewFinderView?.framingRect ?: return null
        val viewWidth = width
        val viewHeight = height

        if (viewWidth <= 0 || viewHeight <= 0) return null

        val rect = RectF(
            framingRect.left.toFloat(),
            framingRect.top.toFloat(),
            framingRect.right.toFloat(),
            framingRect.bottom.toFloat()
        )

        val scaleX = previewWidth.toFloat() / viewWidth.toFloat()
        val scaleY = previewHeight.toFloat() / viewHeight.toFloat()

        val mappedRect = Rect()
        mappedRect.left = (rect.left * scaleX).toInt()
        mappedRect.top = (rect.top * scaleY).toInt()
        mappedRect.right = (rect.right * scaleX).toInt()
        mappedRect.bottom = (rect.bottom * scaleY).toInt()

        return mappedRect
    }

    companion object {
        private const val TAG = "CameraView"
    }
}
