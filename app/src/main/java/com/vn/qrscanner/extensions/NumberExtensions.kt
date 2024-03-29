package com.vn.qrscanner.extensions

import android.content.res.Resources


fun Int.dpToPx() = (Resources.getSystem().displayMetrics.density * this).toInt()
fun Float.dpToPx() = Resources.getSystem().displayMetrics.density * this
