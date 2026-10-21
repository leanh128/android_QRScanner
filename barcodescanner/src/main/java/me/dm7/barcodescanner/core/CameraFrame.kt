package me.dm7.barcodescanner.core

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy

/**
 * One camera frame, handed to [CameraView.CameraPreviewListener.onPreviewFrame]. It wraps the
 * camera buffer, so it is only valid during that call. Reading [lumaGrid] is cheap enough for
 * every frame; [toNv21] is the expensive full copy, so call it only for frames you will decode.
 */
class CameraFrame internal constructor(private val image: ImageProxy) {

    private val rotationDegrees = image.imageInfo.rotationDegrees

    /** Width of the upright frame returned by [toNv21]. */
    val width: Int = if (rotationDegrees % 180 != 0) image.height else image.width

    /** Height of the upright frame returned by [toNv21]. */
    val height: Int = if (rotationDegrees % 180 != 0) image.width else image.height

    /**
     * Average brightness (0-255) of each cell of a [cols] x [rows] grid laid over the frame, read
     * from a few pixels per cell. The grid follows the sensor orientation (not rotated upright),
     * which is fine for comparing frames from the same camera.
     */
    fun lumaGrid(cols: Int = GRID_COLS, rows: Int = GRID_ROWS): FloatArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val cellWidth = maxOf(1, image.width / cols)
        val cellHeight = maxOf(1, image.height / rows)
        val step = maxOf(1, minOf(cellWidth, cellHeight) / SAMPLES_PER_CELL_SIDE)

        val grid = FloatArray(cols * rows)
        for (row in 0 until rows) {
            val top = row * cellHeight
            for (col in 0 until cols) {
                val left = col * cellWidth
                var sum = 0
                var count = 0
                var y = top
                while (y < top + cellHeight && y < image.height) {
                    var x = left
                    while (x < left + cellWidth && x < image.width) {
                        sum += buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
                        count++
                        x += step
                    }
                    y += step
                }
                grid[row * cols + col] = if (count == 0) 0f else sum.toFloat() / count
            }
        }
        return grid
    }

    /** The frame as NV21 bytes, rotated upright ([width] x [height]). */
    fun toNv21(): ByteArray {
        val nv21 = yuv420888ToNv21(image)
        return when (rotationDegrees) {
            90 -> rotateNv21By90(nv21, image.width, image.height)
            180 -> rotateNv21By180(nv21, image.width, image.height)
            270 -> rotateNv21By270(nv21, image.width, image.height)
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
                outputBuffer[offset++] = vBuffer.get(y * vRowStride + x * vPixelStride)
                outputBuffer[offset++] = uBuffer.get(y * uRowStride + x * uPixelStride)
            }
        }

        return outputBuffer
    }

    private fun rotateNv21By90(data: ByteArray, width: Int, height: Int): ByteArray {
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

    private fun rotateNv21By180(data: ByteArray, width: Int, height: Int): ByteArray {
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

    private fun rotateNv21By270(data: ByteArray, width: Int, height: Int): ByteArray {
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

    companion object {
        const val GRID_COLS = 32
        const val GRID_ROWS = 24
        private const val SAMPLES_PER_CELL_SIDE = 4
    }
}
