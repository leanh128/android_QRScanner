package com.vn.qrscanner.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import com.google.zxing.Result
import java.io.ByteArrayOutputStream

object ScannerBitmapUtils {

    private fun calculateCropRect(width: Int, height: Int, result: Result): Rect {
        val points = result.resultPoints
        return if (points != null && points.size >= 2) {
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

            val codeWidth = maxX - minX
            val paddingFactor = 1.5f
            val cropWidth = (codeWidth * paddingFactor).toInt()
            val cropHeight = (cropWidth * 9 / 16)

            val centerX = (minX + maxX) / 2
            val centerY = (minY + maxY) / 2

            Rect(
                maxOf(0, (centerX - cropWidth / 2).toInt()),
                maxOf(0, (centerY - cropHeight / 2).toInt()),
                minOf(width, (centerX + cropWidth / 2).toInt()),
                minOf(height, (centerY + cropHeight / 2).toInt())
            )
        } else {
            // Default fallback crop (center) if points are missing (ML Kit simplified result)
            val size = minOf(width, height) / 2
            Rect(width / 2 - size, height / 2 - size, width / 2 + size, height / 2 + size)
        }
    }

    fun cropResultPreview(
        data: ByteArray,
        width: Int,
        height: Int,
        result: Result
    ): Bitmap? {
        try {
            val rect = calculateCropRect(width, height, result)
            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(rect, 90, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("ScannerBitmapUtils", "Failed to crop preview", e)
            return null
        }
    }

    fun cropBitmap(bitmap: Bitmap, result: Result): Bitmap? {
        return try {
            val rect = calculateCropRect(bitmap.width, bitmap.height, result)
            Bitmap.createBitmap(
                bitmap,
                rect.left,
                rect.top,
                rect.width(),
                rect.height()
            )
        } catch (e: Exception) {
            Log.e("ScannerBitmapUtils", "Failed to crop bitmap", e)
            null
        }
    }
}
