package com.vn.qrscanner.extensions.android

import android.widget.ScrollView

fun ScrollView.scrollToBottom() {
    this.scrollTo(0, bottom)
}

fun ScrollView.smoothScrollToBottom() {
    this.smoothScrollTo(0, bottom)
}