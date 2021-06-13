package org.qr.barcode.scan.lens.extensions

import android.text.TextUtils
import java.util.Locale
import java.util.regex.Pattern


fun String.isLink() : Boolean{
    val linkRegex = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    if(!TextUtils.isEmpty(this))
        return Pattern.compile(linkRegex).matcher(this.lowercase(Locale.ROOT)).matches()
    return false
}