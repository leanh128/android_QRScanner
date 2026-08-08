package com.vn.qrscanner.extensions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

fun Uri.decodeBitmap(context: Context, targetSize: Int = 1280): Bitmap? {
    var inputStream: InputStream? = null
    return try {
        inputStream = context.contentResolver.openInputStream(this)
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
        options.inJustDecodeBounds = false

        inputStream = context.contentResolver.openInputStream(this)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        if (bitmap == null) return null

        // Handle rotation
        inputStream = context.contentResolver.openInputStream(this)
        val exif = ExifInterface(inputStream!!)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        inputStream.close()

        if (orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL) {
            return bitmap
        }

        bitmap.rotate(orientation)
    } catch (e: Exception) {
        Log.e("BitmapExtensions", "Failed to decode bitmap from uri", e)
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

fun Bitmap.rotate(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        else -> return this
    }
    return try {
        val bmRotated = Bitmap.createBitmap(
            this, 0, 0, width, height, matrix, true
        )
        recycle()
        bmRotated
    } catch (e: OutOfMemoryError) {
        e.printStackTrace()
        this
    }
}
