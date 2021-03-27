package org.qr.barcode.scan.lens.extensions

import android.content.res.Resources


fun Int.dpToPx() = (Resources.getSystem().displayMetrics.density * this).toInt()
