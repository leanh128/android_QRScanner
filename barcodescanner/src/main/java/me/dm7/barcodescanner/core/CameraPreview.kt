package me.dm7.barcodescanner.core

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.hardware.Camera
import android.hardware.Camera.AutoFocusCallback
import android.hardware.Camera.PreviewCallback
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

class CameraPreview : SurfaceView, SurfaceHolder.Callback {
    private var mCameraWrapper: CameraWrapper? = null
    private var mAutoFocusHandler: Handler? = null
    private var mPreviewing = true
    private var mAutoFocus = true
    private var mSurfaceCreated = false
    private var mShouldScaleToFill = true
    private var mPreviewCallback: PreviewCallback? = null
    private var mAspectTolerance = 0.1f

    constructor(
        context: Context?,
        cameraWrapper: CameraWrapper?,
        previewCallback: PreviewCallback?
    ) : super(context) {
        init(cameraWrapper, previewCallback)
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        cameraWrapper: CameraWrapper?,
        previewCallback: PreviewCallback?
    ) : super(context, attrs) {
        init(cameraWrapper, previewCallback)
    }

    fun init(cameraWrapper: CameraWrapper?, previewCallback: PreviewCallback?) {
        setCamera(cameraWrapper, previewCallback)
        mAutoFocusHandler = Handler()
        getHolder().addCallback(this)
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    fun setCamera(cameraWrapper: CameraWrapper?, previewCallback: PreviewCallback?) {
        mCameraWrapper = cameraWrapper
        mPreviewCallback = previewCallback
    }

    fun setShouldScaleToFill(scaleToFill: Boolean) {
        mShouldScaleToFill = scaleToFill
    }

    fun setAspectTolerance(aspectTolerance: Float) {
        mAspectTolerance = aspectTolerance
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        mSurfaceCreated = true
    }

    override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i2: Int, i3: Int) {
        if (surfaceHolder.getSurface() == null) {
            return
        }
        stopCameraPreview()
        showCameraPreview()
    }


    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        mSurfaceCreated = false
        stopCameraPreview()
    }

    fun showCameraPreview() {
        if (mCameraWrapper != null) {
            try {
                getHolder().addCallback(this)
                mPreviewing = true
                setupCameraParameters()
                mCameraWrapper!!.mCamera.setPreviewDisplay(getHolder())
                mCameraWrapper!!.mCamera.setDisplayOrientation(this.displayOrientation)
                mCameraWrapper!!.mCamera.setOneShotPreviewCallback(mPreviewCallback)
                mCameraWrapper!!.mCamera.startPreview()
                if (mAutoFocus) {
                    if (mSurfaceCreated) { // check if surface created before using autofocus
                        safeAutoFocus()
                    } else {
                        scheduleAutoFocus() // wait 1 sec and then do check again
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e.toString(), e)
            }
        }
    }

    fun safeAutoFocus() {
        try {
            mCameraWrapper!!.mCamera.autoFocus(autoFocusCB)
        } catch (re: RuntimeException) {
            // Horrible hack to deal with autofocus errors on Sony devices
            // See https://github.com/dm77/barcodescanner/issues/7 for example
            scheduleAutoFocus() // wait 1 sec and then do check again
        }
    }

    fun stopCameraPreview() {
        if (mCameraWrapper != null) {
            try {
                mPreviewing = false
                getHolder().removeCallback(this)
                mCameraWrapper!!.mCamera.cancelAutoFocus()
                mCameraWrapper!!.mCamera.setOneShotPreviewCallback(null)
                mCameraWrapper!!.mCamera.stopPreview()
            } catch (e: Exception) {
                Log.e(TAG, e.toString(), e)
            }
        }
    }

    fun setupCameraParameters() {
        val optimalSize = this.optimalPreviewSize
        val parameters = mCameraWrapper!!.mCamera.getParameters()
        parameters.setPreviewSize(optimalSize!!.width, optimalSize.height)
        mCameraWrapper!!.mCamera.setParameters(parameters)
        adjustViewSize(optimalSize)
    }

    private fun adjustViewSize(cameraSize: Camera.Size) {
        val previewSize = convertSizeToLandscapeOrientation(Point(getWidth(), getHeight()))
        val cameraRatio = (cameraSize.width.toFloat()) / cameraSize.height
        val screenRatio = (previewSize.x.toFloat()) / previewSize.y

        if (screenRatio > cameraRatio) {
            setViewSize((previewSize.y * cameraRatio).toInt(), previewSize.y)
        } else {
            setViewSize(previewSize.x, (previewSize.x / cameraRatio).toInt())
        }
    }

    private fun convertSizeToLandscapeOrientation(size: Point): Point {
        if (this.displayOrientation % 180 == 0) {
            return size
        } else {
            return Point(size.y, size.x)
        }
    }

    private fun setViewSize(width: Int, height: Int) {
        val layoutParams = getLayoutParams()
        var tmpWidth: Int
        var tmpHeight: Int
        if (this.displayOrientation % 180 == 0) {
            tmpWidth = width
            tmpHeight = height
        } else {
            tmpWidth = height
            tmpHeight = width
        }

        if (mShouldScaleToFill) {
            val parentWidth = (getParent() as View).getWidth()
            val parentHeight = (getParent() as View).getHeight()
            val ratioWidth = parentWidth.toFloat() / tmpWidth.toFloat()
            val ratioHeight = parentHeight.toFloat() / tmpHeight.toFloat()

            val compensation: Float

            if (ratioWidth > ratioHeight) {
                compensation = ratioWidth
            } else {
                compensation = ratioHeight
            }

            tmpWidth = Math.round(tmpWidth * compensation)
            tmpHeight = Math.round(tmpHeight * compensation)
        }

        layoutParams.width = tmpWidth
        layoutParams.height = tmpHeight
        setLayoutParams(layoutParams)
    }

    val displayOrientation: Int
        get() {
            if (mCameraWrapper == null) {
                //If we don't have a camera set there is no orientation so return dummy value
                return 0
            }

            val info = Camera.CameraInfo()
            if (mCameraWrapper!!.mCameraId == -1) {
                Camera.getCameraInfo(
                    Camera.CameraInfo.CAMERA_FACING_BACK,
                    info
                )
            } else {
                Camera.getCameraInfo(mCameraWrapper!!.mCameraId, info)
            }

            val wm =
                getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.getDefaultDisplay()

            val rotation = display.getRotation()
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
            }

            var result: Int
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360
                result = (360 - result) % 360 // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360
            }
            return result
        }

    private val optimalPreviewSize: Camera.Size?
        get() {
            if (mCameraWrapper == null) {
                return null
            }

            val sizes =
                mCameraWrapper!!.mCamera.getParameters().getSupportedPreviewSizes()
            var w = getWidth()
            var h = getHeight()
            if (DisplayUtils.getScreenOrientation(getContext()) == Configuration.ORIENTATION_PORTRAIT) {
                val portraitWidth = h
                h = w
                w = portraitWidth
            }

            val targetRatio = w.toDouble() / h
            if (sizes == null) return null

            var optimalSize: Camera.Size? = null
            var minDiff = Double.MAX_VALUE

            val targetHeight = h

            // Try to find an size match aspect ratio and size
            for (size in sizes) {
                val ratio = size.width.toDouble() / size.height
                if (abs(ratio - targetRatio) > mAspectTolerance) continue
                if (abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.height - targetHeight).toDouble()
                }
            }

            // Cannot find the one match the aspect ratio, ignore the requirement
            if (optimalSize == null) {
                minDiff = Double.MAX_VALUE
                for (size in sizes) {
                    if (abs(size.height - targetHeight) < minDiff) {
                        optimalSize = size
                        minDiff = abs(size.height - targetHeight).toDouble()
                    }
                }
            }
            return optimalSize
        }

    fun setAutoFocus(state: Boolean) {
        if (mCameraWrapper != null && mPreviewing) {
            if (state == mAutoFocus) {
                return
            }
            mAutoFocus = state
            if (mAutoFocus) {
                if (mSurfaceCreated) { // check if surface created before using autofocus
                    Log.v(TAG, "Starting autofocus")
                    safeAutoFocus()
                } else {
                    scheduleAutoFocus() // wait 1 sec and then do check again
                }
            } else {
                Log.v(TAG, "Cancelling autofocus")
                mCameraWrapper!!.mCamera.cancelAutoFocus()
            }
        }
    }

    private val doAutoFocus: Runnable = object : Runnable {
        override fun run() {
            if (mCameraWrapper != null && mPreviewing && mAutoFocus && mSurfaceCreated) {
                safeAutoFocus()
            }
        }
    }

    // Mimic continuous auto-focusing
    var autoFocusCB: AutoFocusCallback = object : AutoFocusCallback {
        override fun onAutoFocus(success: Boolean, camera: Camera?) {
            scheduleAutoFocus()
        }
    }

    private fun scheduleAutoFocus() {
        mAutoFocusHandler!!.postDelayed(doAutoFocus, 1000)
    }

    companion object {
        private const val TAG = "CameraPreview"
    }
}
