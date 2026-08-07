package com.vn.qrscanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.Result
import java.io.ByteArrayOutputStream
import java.io.InputStream

object BitmapUtils {

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
            Log.e("BitmapUtils", "Failed to crop preview", e)
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
            Log.e("BitmapUtils", "Failed to crop bitmap", e)
            null
        }
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val targetSize = 1280
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
            options.inJustDecodeBounds = false

            inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            if (bitmap == null) return null

            // Handle rotation
            inputStream = context.contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            inputStream.close()

            if (orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL) {
                return bitmap
            }

            rotateBitmap(bitmap, orientation)
        } catch (e: Exception) {
            Log.e("BitmapUtils", "Failed to decode bitmap from uri", e)
            null
        } finally {
            inputStream?.close()
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return try {
            val bmRotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            bitmap.recycle()
            bmRotated
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            bitmap
        }
    }
}
