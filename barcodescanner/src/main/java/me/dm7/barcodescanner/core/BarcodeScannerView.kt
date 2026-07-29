package me.dm7.barcodescanner.core

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import me.dm7.barcodescanner.R

abstract class BarcodeScannerView : FrameLayout, PreviewCallback {
    private var mCameraWrapper: CameraWrapper? = null
    private var mPreview: CameraPreview? = null
    private var mViewFinderView: IViewFinder? = null
    private var mFramingRectInPreview: Rect? = null
    private var mCameraHandlerThread: CameraHandlerThread? = null
    private var mFlashState: Boolean? = null
    private var mAutofocusState = true
    private var mShouldScaleToFill = true

    private var mIsLaserEnabled = true

    @ColorInt
    private var mLaserColor = getResources().getColor(R.color.viewfinder_laser)

    @ColorInt
    private var mBorderColor = getResources().getColor(R.color.viewfinder_border)
    private var mMaskColor = getResources().getColor(R.color.viewfinder_mask)
    private var mBorderWidth = getResources().getInteger(R.integer.viewfinder_border_width)
    private var mBorderLength = getResources().getInteger(R.integer.viewfinder_border_length)
    private var mRoundedCorner = false
    private var mCornerRadius = 0
    private var mSquaredFinder = false
    private var mBorderAlpha = 1.0f
    private var mViewFinderOffset = 0
    private var mAspectTolerance = 0.1f

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet) {
        val a = context.getTheme().obtainStyledAttributes(
            attributeSet,
            R.styleable.BarcodeScannerView,
            0, 0
        )

        try {
            setShouldScaleToFill(
                a.getBoolean(
                    R.styleable.BarcodeScannerView_shouldScaleToFill,
                    true
                )
            )
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

        init()
    }

    private fun init() {
        mViewFinderView = createViewFinderView(getContext())
    }

    fun setupLayout(cameraWrapper: CameraWrapper?) {
        removeAllViews()

        mPreview = CameraPreview(getContext(), cameraWrapper, this)
        mPreview!!.setAspectTolerance(mAspectTolerance)
        mPreview!!.setShouldScaleToFill(mShouldScaleToFill)
        if (!mShouldScaleToFill) {
            val relativeLayout = RelativeLayout(getContext())
            relativeLayout.setGravity(Gravity.CENTER)
            relativeLayout.setBackgroundColor(Color.BLACK)
            relativeLayout.addView(mPreview)
            addView(relativeLayout)
        } else {
            addView(mPreview)
        }

        if (mViewFinderView is View) {
            addView(mViewFinderView as View)
        } else {
            throw IllegalArgumentException(
                "IViewFinder object returned by " +
                        "'createViewFinderView()' should be instance of android.view.View"
            )
        }
    }

    /**
     * 
     * Method that creates view that represents visual appearance of a barcode scanner
     * 
     * Override it to provide your own view for visual appearance of a barcode scanner
     * 
     * @param context [Context]
     * @return [View] that implements [ViewFinderView]
     */
    protected fun createViewFinderView(context: Context?): IViewFinder {
        val viewFinderView = ViewFinderView(context)
        viewFinderView.setBorderColor(mBorderColor)
        viewFinderView.setLaserColor(mLaserColor)
        viewFinderView.setLaserEnabled(mIsLaserEnabled)
        viewFinderView.setBorderStrokeWidth(mBorderWidth)
        viewFinderView.setBorderLineLength(mBorderLength)
        viewFinderView.setMaskColor(mMaskColor)

        viewFinderView.setBorderCornerRounded(mRoundedCorner)
        viewFinderView.setBorderCornerRadius(mCornerRadius)
        viewFinderView.setSquareViewFinder(mSquaredFinder)
        viewFinderView.setViewFinderOffset(mViewFinderOffset)
        return viewFinderView
    }

    fun zsetLaserColor(laserColor: Int) {
        mLaserColor = laserColor
        mViewFinderView!!.setLaserColor(mLaserColor)
        mViewFinderView!!.setupViewFinder()
    }

    fun setMaskColor(maskColor: Int) {
        mMaskColor = maskColor
        mViewFinderView!!.setMaskColor(mMaskColor)
        mViewFinderView!!.setupViewFinder()
    }

    fun setBorderColor(borderColor: Int) {
        mBorderColor = borderColor
        mViewFinderView!!.setBorderColor(mBorderColor)
        mViewFinderView!!.setupViewFinder()
    }

    fun setBorderStrokeWidth(borderStrokeWidth: Int) {
        mBorderWidth = borderStrokeWidth
        mViewFinderView!!.setBorderStrokeWidth(mBorderWidth)
        mViewFinderView!!.setupViewFinder()
    }

    fun setBorderLineLength(borderLineLength: Int) {
        mBorderLength = borderLineLength
        mViewFinderView!!.setBorderLineLength(mBorderLength)
        mViewFinderView!!.setupViewFinder()
    }

    fun setLaserEnabled(isLaserEnabled: Boolean) {
        mIsLaserEnabled = isLaserEnabled
        mViewFinderView!!.setLaserEnabled(mIsLaserEnabled)
        mViewFinderView!!.setupViewFinder()
    }

    fun setIsBorderCornerRounded(isBorderCornerRounded: Boolean) {
        mRoundedCorner = isBorderCornerRounded
        mViewFinderView!!.setBorderCornerRounded(mRoundedCorner)
        mViewFinderView!!.setupViewFinder()
    }

    fun setBorderCornerRadius(borderCornerRadius: Int) {
        mCornerRadius = borderCornerRadius
        mViewFinderView!!.setBorderCornerRadius(mCornerRadius)
        mViewFinderView!!.setupViewFinder()
    }

    fun setSquareViewFinder(isSquareViewFinder: Boolean) {
        mSquaredFinder = isSquareViewFinder
        mViewFinderView!!.setSquareViewFinder(mSquaredFinder)
        mViewFinderView!!.setupViewFinder()
    }

    fun setBorderAlpha(borderAlpha: Float) {
        mBorderAlpha = borderAlpha
        mViewFinderView!!.setBorderAlpha(mBorderAlpha)
        mViewFinderView!!.setupViewFinder()
    }

    @JvmOverloads
    fun startCamera(cameraId: Int = CameraUtils.defaultCameraId) {
        if (mCameraHandlerThread == null) {
            mCameraHandlerThread = CameraHandlerThread(this)
        }
        mCameraHandlerThread!!.startCamera(cameraId)
    }

    fun setupCameraPreview(cameraWrapper: CameraWrapper?) {
        mCameraWrapper = cameraWrapper
        if (mCameraWrapper != null) {
            setupLayout(mCameraWrapper)
            mViewFinderView!!.setupViewFinder()
            if (mFlashState != null) {
                setFlash(mFlashState!!)
            }
            setAutoFocus(mAutofocusState)
        }
    }

    fun stopCamera() {
        if (mCameraWrapper != null) {
            mPreview!!.stopCameraPreview()
            mPreview!!.setCamera(null, null)
            mCameraWrapper!!.mCamera.release()
            mCameraWrapper = null
        }
        if (mCameraHandlerThread != null) {
            mCameraHandlerThread!!.quit()
            mCameraHandlerThread = null
        }
    }

    fun stopCameraPreview() {
        if (mPreview != null) {
            mPreview!!.stopCameraPreview()
        }
    }

    protected fun resumeCameraPreview() {
        if (mPreview != null) {
            mPreview!!.showCameraPreview()
        }
    }

    @Synchronized
    fun getFramingRectInPreview(previewWidth: Int, previewHeight: Int): Rect? {
        if (mFramingRectInPreview == null) {
            val framingRect = mViewFinderView!!.framingRect
            val viewFinderViewWidth = mViewFinderView!!.viewWidth
            val viewFinderViewHeight = mViewFinderView!!.viewHeight
            if (framingRect == null || viewFinderViewWidth == 0 || viewFinderViewHeight == 0) {
                return null
            }

            val rect = Rect(framingRect)

            if (previewWidth < viewFinderViewWidth) {
                rect.left = rect.left * previewWidth / viewFinderViewWidth
                rect.right = rect.right * previewWidth / viewFinderViewWidth
            }

            if (previewHeight < viewFinderViewHeight) {
                rect.top = rect.top * previewHeight / viewFinderViewHeight
                rect.bottom = rect.bottom * previewHeight / viewFinderViewHeight
            }

            mFramingRectInPreview = rect
        }
        return mFramingRectInPreview
    }

    fun setFlash(flag: Boolean) {
        mFlashState = flag
        if (mCameraWrapper != null && CameraUtils.isFlashSupported(mCameraWrapper!!.mCamera)) {
            val parameters = mCameraWrapper!!.mCamera.getParameters()
            if (flag) {
                if (parameters.getFlashMode() == Camera.Parameters.FLASH_MODE_TORCH) {
                    return
                }
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
            } else {
                if (parameters.getFlashMode() == Camera.Parameters.FLASH_MODE_OFF) {
                    return
                }
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF)
            }
            mCameraWrapper!!.mCamera.setParameters(parameters)
        }
    }

    fun setAutoFocus(state: Boolean) {
        mAutofocusState = state
        if (mPreview != null) {
            mPreview!!.setAutoFocus(state)
        }
    }

    fun setShouldScaleToFill(shouldScaleToFill: Boolean) {
        mShouldScaleToFill = shouldScaleToFill
    }

    fun setAspectTolerance(aspectTolerance: Float) {
        mAspectTolerance = aspectTolerance
    }

    fun getRotatedData(data: ByteArray, camera: Camera): ByteArray {
        var data = data
        val parameters = camera.getParameters()
        val size = parameters.getPreviewSize()
        var width = size.width
        var height = size.height

        val rotationCount = this.rotationCount

        if (rotationCount == 1 || rotationCount == 3) {
            for (i in 0..<rotationCount) {
                val rotatedData = ByteArray(data.size)
                for (y in 0..<height) {
                    for (x in 0..<width) rotatedData[x * height + height - y - 1] =
                        data[x + y * width]
                }
                data = rotatedData
                val tmp = width
                width = height
                height = tmp
            }
        }

        return data
    }

    val rotationCount: Int
        get() {
            val displayOrientation = mPreview!!.displayOrientation
            return displayOrientation / 90
        }
}

