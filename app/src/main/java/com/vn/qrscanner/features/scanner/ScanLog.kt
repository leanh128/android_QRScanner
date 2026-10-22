package com.vn.qrscanner.features.scanner

import android.util.Log
import com.vn.qrscanner.BuildConfig
import java.util.Locale

/**
 * Debug logs for tuning camera scanning, on in debug builds only.
 * - `adb logcat -s QrScan` shows phase changes, decodes and results.
 * - `adb logcat -s QrScanFrame:V` adds one line per camera frame (motion measurements).
 */
internal object ScanLog {
    const val TAG = "QrScan"
    const val FRAME_TAG = "QrScanFrame"

    val enabled = BuildConfig.DEBUG

    inline fun d(message: () -> String) {
        if (enabled) Log.d(TAG, message())
    }

    inline fun frame(message: () -> String) {
        if (enabled) Log.v(FRAME_TAG, message())
    }

    fun f(value: Float): String = String.format(Locale.US, "%.1f", value)

    fun ms(startNanos: Long): String =
        String.format(Locale.US, "%.1fms", (System.nanoTime() - startNanos) / 1_000_000f)
}
