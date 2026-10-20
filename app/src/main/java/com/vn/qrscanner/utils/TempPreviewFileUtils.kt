package com.vn.qrscanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object TempPreviewFileUtils {
    private const val PREVIEW_CACHE_DIR = "scan_previews"

    /** Saves one preview. Call [clearCache] first to start a fresh batch; this keeps earlier files. */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap): String? {
        return try {
            val cacheDir = File(context.cacheDir, PREVIEW_CACHE_DIR)
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val file = File(cacheDir, "preview_${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to save bitmap to cache", e)
            null
        }
    }

    fun clearCache(context: Context, exceptFilePaths: Collection<String> = emptyList()) {
        try {
            val cacheDir = File(context.cacheDir, PREVIEW_CACHE_DIR)
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { 
                    if (it.absolutePath !in exceptFilePaths) {
                        it.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Failed to clear cache", e)
        }
    }
}