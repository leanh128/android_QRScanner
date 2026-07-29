package me.dm7.barcodescanner.core

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper


// This code is mostly based on the top answer here: http://stackoverflow.com/questions/18149964/best-use-of-handlerthread-over-other-similar-classes
class CameraHandlerThread(private val mCameraView: CameraView) :
    HandlerThread("CameraHandlerThread") {
    init {
        start()
    }

    fun startCamera(cameraId: Int) {
        val localHandler = Handler(looper)
        localHandler.post {
            val camera = CameraUtils.getCameraInstance(cameraId)
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                mCameraView.setupCameraPreview(
                    CameraWrapper.getWrapper(
                        camera,
                        cameraId
                    )
                )
            }
        }
    }

    companion object {
        private const val LOG_TAG = "CameraHandlerThread"
    }
}
