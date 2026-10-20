package com.vn.qrscanner.utils

import android.app.Activity

/** Safe to call from any thread. Identical consecutive toasts are dropped and the queue is capped. */
fun Activity.showToast(
    message: String,
    type: ToastType,
    durationMs: Long = ToastHost.DEFAULT_DURATION_MS
) {
    runOnUiThread {
        ToastHost.of(this)?.show(ToastItem(message, type, durationMs))
    }
}

fun Activity.showInfoToast(message: String, durationMs: Long = ToastHost.DEFAULT_DURATION_MS) =
    showToast(message, ToastType.INFO, durationMs)

fun Activity.showWarningToast(message: String, durationMs: Long = ToastHost.DEFAULT_DURATION_MS) =
    showToast(message, ToastType.WARNING, durationMs)

fun Activity.showErrorToast(message: String, durationMs: Long = ToastHost.DEFAULT_DURATION_MS) =
    showToast(message, ToastType.ERROR, durationMs)

fun Activity.showSuccessToast(message: String, durationMs: Long = ToastHost.DEFAULT_DURATION_MS) =
    showToast(message, ToastType.SUCCESS, durationMs)
