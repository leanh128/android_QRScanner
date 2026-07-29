package me.dm7.barcodescanner.core

import android.hardware.Camera

object CameraUtils {
    val cameraInstance: Camera?
        /** A safe way to get an instance of the Camera object.  */
        get() = getCameraInstance(defaultCameraId)

    val defaultCameraId: Int
        /** Favor back-facing camera by default. If none exists, fallback to whatever camera is available  */
        get() {
            val numberOfCameras = Camera.getNumberOfCameras()
            val cameraInfo =
                Camera.CameraInfo()
            var defaultCameraId = -1
            for (i in 0..<numberOfCameras) {
                defaultCameraId = i
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    return i
                }
            }
            return defaultCameraId
        }

    /** A safe way to get an instance of the Camera object.  */
    fun getCameraInstance(cameraId: Int): Camera? {
        var c: Camera? = null
        try {
            if (cameraId == -1) {
                c = Camera.open() // attempt to get a Camera instance
            } else {
                c = Camera.open(cameraId) // attempt to get a Camera instance
            }
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
        }
        return c // returns null if camera is unavailable
    }

    fun isFlashSupported(camera: Camera?): Boolean {
        /* Credits: Top answer at http://stackoverflow.com/a/19599365/868173 */
        if (camera != null) {
            val parameters = camera.getParameters()

            if (parameters.getFlashMode() == null) {
                return false
            }

            val supportedFlashModes = parameters.getSupportedFlashModes()
            if (supportedFlashModes == null || supportedFlashModes.isEmpty() || supportedFlashModes.size == 1 && supportedFlashModes.get(
                    0
                ) == Camera.Parameters.FLASH_MODE_OFF
            ) {
                return false
            }
        } else {
            return false
        }

        return true
    }
}