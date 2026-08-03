package com.vn.qrscanner.extensions

import android.net.Uri
import android.text.TextUtils
import android.util.Patterns
import androidx.core.net.toUri


fun String.isLink(): Boolean {
    return !TextUtils.isEmpty(this) && Patterns.WEB_URL.matcher(this).matches()
}

fun String.toWebUri(): Uri {
    val uri = toUri()
    if (uri.scheme != null) return uri
    return "https://$this".toUri()
}
