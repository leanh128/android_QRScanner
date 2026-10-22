package me.dm7.barcodescanner.core

import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy

/**
 * One camera frame, handed to [CameraView.CameraPreviewListener.onPreviewFrame]. It wraps the
 * camera buffer, so it is only valid during that call. [sharpness] reads a few thousand pixels;
 * [toNv21] is a full copy, so call it only for frames you will decode.
 */
class CameraFrame internal constructor(private val image: ImageProxy) {

    private val rotationDegrees = image.imageInfo.rotationDegrees

    /** Width of the upright frame returned by [toNv21]. */
    val width: Int = if (rotationDegrees % 180 != 0) image.height else image.width

    /** Height of the upright frame returned by [toNv21]. */
    val height: Int = if (rotationDegrees % 180 != 0) image.width else image.height

    /**
     * How sharp the picture is inside [area] (upright frame coordinates, null for the whole
     * frame): the variance of the Laplacian of the brightness, so higher is sharper and a blurry
     * or empty area scores near 0. About [SHARPNESS_SAMPLES] x [SHARPNESS_SAMPLES] pixels are
     * read whatever the frame size, so the score does not depend on the resolution.
     */
    fun sharpness(area: Rect?): Float {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val roi = toSensorRect(area)
        val step = maxOf(1, minOf(roi.width(), roi.height()) / SHARPNESS_SAMPLES)

        // Three rows are copied at a time: reading the direct buffer byte by byte is far slower.
        val rowWidth = roi.width()
        val above = ByteArray(rowWidth)
        val middle = ByteArray(rowWidth)
        val below = ByteArray(rowWidth)
        fun readRow(into: ByteArray, y: Int) {
            if (pixelStride == 1) {
                buffer.position(y * rowStride + roi.left)
                buffer.get(into, 0, rowWidth)
            } else {
                for (i in 0 until rowWidth) {
                    into[i] = buffer.get(y * rowStride + (roi.left + i) * pixelStride)
                }
            }
        }
        fun at(row: ByteArray, x: Int) = row[x].toInt() and 0xFF

        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        var y = roi.top + step
        while (y < roi.bottom - step) {
            readRow(above, y - step)
            readRow(middle, y)
            readRow(below, y + step)
            var x = step
            while (x < rowWidth - step) {
                val laplacian = 4 * at(middle, x) - at(middle, x - step) - at(middle, x + step) -
                    at(above, x) - at(below, x)
                sum += laplacian
                sumSquares += laplacian.toDouble() * laplacian
                count++
                x += step
            }
            y += step
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSquares / count - mean * mean).toFloat()
    }

    /** [area] (upright coordinates) as a rectangle in the sensor's own orientation, clamped. */
    private fun toSensorRect(area: Rect?): Rect {
        val full = Rect(0, 0, image.width, image.height)
        if (area == null) return full
        val left = area.left.coerceIn(0, width - 1)
        val top = area.top.coerceIn(0, height - 1)
        val right = (area.right - 1).coerceIn(left, width - 1)
        val bottom = (area.bottom - 1).coerceIn(top, height - 1)
        // Same turns as the rotation done in toNv21, undone for the two opposite corners.
        fun toSensor(x: Int, y: Int): Pair<Int, Int> = when (rotationDegrees) {
            90 -> y to image.height - 1 - x
            180 -> image.width - 1 - x to image.height - 1 - y
            270 -> image.width - 1 - y to x
            else -> x to y
        }
        val (x1, y1) = toSensor(left, top)
        val (x2, y2) = toSensor(right, bottom)
        val rect = Rect(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2) + 1, maxOf(y1, y2) + 1)
        return if (rect.intersect(full)) rect else full
    }

    /** The frame as NV21 bytes, rotated upright ([width] x [height]). */
    fun toNv21(): ByteArray {
        val nv21 = yuv420888ToNv21(image)
        return when (rotationDegrees) {
            90 -> Nv21Rotation.by90(nv21, image.width, image.height)
            180 -> Nv21Rotation.by180(nv21, image.width, image.height)
            270 -> Nv21Rotation.by270(nv21, image.width, image.height)
            else -> nv21
        }
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
        val outputBuffer = ByteArray(width * height * pixelSizeBits / 8)
        val imagePlanes = image.planes

        // Y plane
        val yPlane = imagePlanes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        var offset = 0
        for (y in 0 until height) {
            if (yPixelStride == 1) {
                // Whole row at once: much faster than byte by byte.
                yBuffer.position(y * yRowStride)
                yBuffer.get(outputBuffer, offset, width)
                offset += width
            } else {
                for (x in 0 until width) {
                    outputBuffer[offset++] = yBuffer.get(y * yRowStride + x * yPixelStride)
                }
            }
        }

        // UV planes (interleaved for NV21: V, U, V, U...). Rows are copied in one go and read from
        // the array: reading the direct buffer byte by byte is far slower.
        val vPlane = imagePlanes[2]
        val uPlane = imagePlanes[1]
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        val vRow = ByteArray(minOf(vBuffer.capacity(), (width / 2 - 1) * vPixelStride + 1))
        val uRow = ByteArray(minOf(uBuffer.capacity(), (width / 2 - 1) * uPixelStride + 1))
        for (y in 0 until height / 2) {
            vBuffer.position(y * vRowStride)
            vBuffer.get(vRow, 0, minOf(vRow.size, vBuffer.remaining()))
            uBuffer.position(y * uRowStride)
            uBuffer.get(uRow, 0, minOf(uRow.size, uBuffer.remaining()))
            for (x in 0 until width / 2) {
                outputBuffer[offset++] = vRow[x * vPixelStride]
                outputBuffer[offset++] = uRow[x * uPixelStride]
            }
        }

        return outputBuffer
    }

    companion object {
        /** Pixels read along the shorter side of the area by [sharpness]. */
        const val SHARPNESS_SAMPLES = 160
    }
}
