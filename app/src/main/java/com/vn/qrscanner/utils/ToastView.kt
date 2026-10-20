package com.vn.qrscanner.utils

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import com.vn.qrscanner.R
import com.vn.qrscanner.databinding.LayoutCustomToastBinding

/**
 * A single toast bubble: icon + message, with scale/fade enter and exit animations.
 * It knows nothing about queuing; see [ToastHost].
 */
class ToastView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding: LayoutCustomToastBinding

    /** Called when this view leaves the window, however that happens. */
    internal var onDetached: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val horizontal = resources.getDimensionPixelSize(R.dimen.toast_padding_horizontal)
        val vertical = resources.getDimensionPixelSize(R.dimen.toast_padding_vertical)
        setPadding(horizontal, vertical, horizontal, vertical)
        setBackgroundResource(R.drawable.bg_custom_toast)
        elevation = resources.getDimension(R.dimen.toast_elevation)
        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        binding = LayoutCustomToastBinding.inflate(LayoutInflater.from(context), this)
        alpha = 0f
    }

    fun bind(item: ToastItem) {
        binding.tvToastMessage.text = item.message
        binding.ivToastIcon.setImageResource(iconFor(item.type))
    }

    fun animateIn() {
        scaleX = ENTER_START_SCALE
        scaleY = ENTER_START_SCALE
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun animateOut(onEnd: () -> Unit) {
        animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION_MS)
            .withEndAction(onEnd)
            .start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pivotX = w / 2f
        pivotY = h / 2f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animate().cancel()
        onDetached?.invoke()
    }

    @DrawableRes
    private fun iconFor(type: ToastType): Int = when (type) {
        ToastType.INFO -> R.drawable.ic_info
        ToastType.WARNING -> R.drawable.ic_warning
        ToastType.ERROR -> R.drawable.ic_error
        ToastType.SUCCESS -> R.drawable.ic_success
    }

    private companion object {
        const val ANIM_DURATION_MS = 220L
        const val ENTER_START_SCALE = 0.2f
    }
}
