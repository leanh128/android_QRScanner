package org.qr.barcode.scan.lens.extensions

import android.content.res.Resources


fun Float.dpToPx() = (Resources.getSystem().displayMetrics.density * this).toInt()
fun Int.dpToPx() = (Resources.getSystem().displayMetrics.density * this).toInt()
