package me.dm7.barcodescanner.core

import android.hardware.Camera

class CameraWrapper private constructor(val mCamera: Camera, val mCameraId: Int) {
    companion object {
        fun getWrapper(camera: Camera?, cameraId: Int): CameraWrapper? {
            return if (camera == null) {
                null
            } else {
                CameraWrapper(camera, cameraId)
            }
        }
    }
}
