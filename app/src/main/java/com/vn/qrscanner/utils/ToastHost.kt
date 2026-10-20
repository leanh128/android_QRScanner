package com.vn.qrscanner.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.vn.qrscanner.R
import java.util.ArrayDeque

enum class ToastType { INFO, WARNING, ERROR, SUCCESS }

data class ToastItem(
    val message: String,
    val type: ToastType,
    val durationMs: Long = ToastHost.DEFAULT_DURATION_MS
)

/**
 * Shows [ToastView]s one at a time on top of an activity's content root.
 *
 * One host exists per content root (stored as a view tag), so state dies with the activity
 * instead of living in a process-wide singleton. All methods must run on the main thread.
 */
@MainThread
class ToastHost private constructor(private val rootView: ViewGroup) {

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<ToastItem>()
    private var current: ToastView? = null
    private var currentItem: ToastItem? = null

    private val dismissRunnable = Runnable { dismissCurrent() }
    private val showNextRunnable = Runnable { showNext() }

    fun show(item: ToastItem) {
        if (isDuplicate(item)) return
        if (queue.size >= MAX_QUEUE_SIZE) queue.poll()
        queue.add(item)
        if (current == null) showNext()
    }

    private fun isDuplicate(item: ToastItem): Boolean {
        val last = queue.peekLast() ?: currentItem
        return last != null && last.message == item.message && last.type == item.type
    }

    private fun showNext() {
        if (current != null) return
        if (isActivityGone()) {
            queue.clear()
            return
        }
        val item = queue.poll() ?: return

        val toastView = ToastView(rootView.context).apply {
            bind(item)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val horizontal = resources.getDimensionPixelSize(R.dimen.toast_margin_horizontal)
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                marginStart = horizontal
                marginEnd = horizontal
                bottomMargin = resources.getDimensionPixelSize(R.dimen.toast_margin_bottom) +
                    bottomInset()
            }
            onDetached = { onToastDetached(this) }
        }
        current = toastView
        currentItem = item
        rootView.addView(toastView)
        toastView.animateIn()
        handler.postDelayed(dismissRunnable, item.durationMs)
    }

    private fun dismissCurrent() {
        val toastView = current ?: return
        toastView.animateOut { rootView.removeView(toastView) }
    }

    /**
     * Single exit point for a toast, whether we removed it or the window went away.
     * Advancing is posted so we never add a view while another is mid-removal.
     */
    private fun onToastDetached(toastView: ToastView) {
        if (toastView !== current) return
        current = null
        currentItem = null
        handler.removeCallbacks(dismissRunnable)
        if (isActivityGone()) {
            queue.clear()
            handler.removeCallbacks(showNextRunnable)
        } else {
            handler.post(showNextRunnable)
        }
    }

    private fun bottomInset(): Int =
        ViewCompat.getRootWindowInsets(rootView)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            ?.bottom ?: 0

    private fun isActivityGone(): Boolean {
        val activity = rootView.context.findActivity() ?: return false
        return activity.isFinishing || activity.isDestroyed
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    companion object {
        const val DEFAULT_DURATION_MS = 2000L
        private const val MAX_QUEUE_SIZE = 3

        /** Returns the host for [activity], or null if its content root is not available yet. */
        fun of(activity: Activity): ToastHost? {
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
            return (root.getTag(R.id.toast_host) as? ToastHost)
                ?: ToastHost(root).also { root.setTag(R.id.toast_host, it) }
        }
    }
}
