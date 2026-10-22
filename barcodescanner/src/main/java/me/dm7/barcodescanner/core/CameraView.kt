package me.dm7.barcodescanner.core

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Size
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import me.dm7.barcodescanner.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

open class CameraView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr) {

    interface CameraPreviewListener {
        /** Called on a background thread for every frame; [frame] is only valid during the call. */
        fun onPreviewFrame(frame: CameraFrame)
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
    private var mImageAnalysis: ImageAnalysis? = null
    private var mAnalysisExecutor: ExecutorService? = null

    init {
        // getFramingRectInPreview relies on this.
        mPreviewView.scaleType = PreviewView.ScaleType.FILL_CENTER
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

        // Without a resolution CameraX analyses at 640x480, too coarse to read small or several
        // codes at once.
        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    mTargetAspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(analysisResolutionSelector)
            .build()
            .also {
                // Frames are handled off the main thread so reading them never blocks the UI.
                it.setAnalyzer(analysisExecutor()) { imageProxy ->
                    imageProxy.use { image -> listener.onPreviewFrame(CameraFrame(image)) }
                }
            }
        mImageAnalysis?.clearAnalyzer()
        mImageAnalysis = imageAnalysis

        val builder = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageAnalysis)

        mPreviewView.viewPort?.let {
            builder.setViewPort(it)
        }

        return builder.build()
    }

    private fun analysisExecutor(): ExecutorService =
        mAnalysisExecutor ?: Executors.newSingleThreadExecutor().also { mAnalysisExecutor = it }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mImageAnalysis?.clearAnalyzer()
        mImageAnalysis = null
        mAnalysisExecutor?.shutdown()
        mAnalysisExecutor = null
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

        if (viewWidth <= 0 || viewHeight <= 0 || previewWidth <= 0 || previewHeight <= 0) return null

        val rect = RectF(
            framingRect.left.toFloat(),
            framingRect.top.toFloat(),
            framingRect.right.toFloat(),
            framingRect.bottom.toFloat()
        )

        // The preview fills the view and is centred, so one side of it is cropped: the frame is
        // scaled by one factor (not by view size / frame size on each axis) and the cropped part
        // is not on screen.
        val scale = maxOf(viewWidth / previewWidth.toFloat(), viewHeight / previewHeight.toFloat())
        val offsetX = (previewWidth * scale - viewWidth) / 2f
        val offsetY = (previewHeight * scale - viewHeight) / 2f

        val mappedRect = Rect()
        mappedRect.left = ((rect.left + offsetX) / scale).toInt()
        mappedRect.top = ((rect.top + offsetY) / scale).toInt()
        mappedRect.right = ((rect.right + offsetX) / scale).toInt()
        mappedRect.bottom = ((rect.bottom + offsetY) / scale).toInt()

        return mappedRect
    }

    companion object {
        private const val TAG = "CameraView"

        /** Size of the frames handed to the listener (landscape sensor orientation, 4:3). */
        private const val ANALYSIS_WIDTH = 1280
        private const val ANALYSIS_HEIGHT = 960
    }
}
