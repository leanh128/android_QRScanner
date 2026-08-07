package com.vn.qrscanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object TempPreviewFileUtils {
    private const val PREVIEW_CACHE_DIR = "scan_previews"

    fun saveBitmapToCache(context: Context, bitmap: Bitmap): String? {
        return try {
            val cacheDir = File(context.cacheDir, PREVIEW_CACHE_DIR)
            if (!cacheDir.exists()) cacheDir.mkdirs()

            // Clear previous previews to keep folder clean
            clearCache(context)

            val file = File(cacheDir, "preview_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.flush()
            out.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to save bitmap to cache", e)
            null
        }
    }

    fun clearCache(context: Context, exceptFilePath: String? = null) {
        try {
            val cacheDir = File(context.cacheDir, PREVIEW_CACHE_DIR)
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { 
                    if (exceptFilePath == null || it.absolutePath != exceptFilePath) {
                        it.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to clear cache", e)
        }
    }
}