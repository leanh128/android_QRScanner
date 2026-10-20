package com.vn.qrscanner.utils

import android.app.Activity
import android.view.ViewGroup
import android.widget.TextView
import com.vn.qrscanner.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class ToastHostTest {

    private lateinit var activity: Activity
    private lateinit var host: ToastHost
    private lateinit var root: ViewGroup

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.AppTheme)
        root = activity.findViewById(android.R.id.content)
        host = ToastHost.of(activity)!!
    }

    private fun toastCount() = (0 until root.childCount).count { root.getChildAt(it) is ToastView }

    private fun idle(ms: Long) = shadowOf(android.os.Looper.getMainLooper()).idleFor(
        java.time.Duration.ofMillis(ms)
    )

    @Test
    fun hostIsReusedPerActivity() {
        assertSame(host, ToastHost.of(activity))
    }

    @Test
    fun showsOneToastAtATimeThenAdvances() {
        host.show(ToastItem("one", ToastType.INFO, 1000))
        host.show(ToastItem("two", ToastType.ERROR, 1000))
        assertEquals(1, toastCount())

        idle(2000)
        assertEquals(1, toastCount()) // "two" replaced "one"

        idle(2000)
        assertEquals(0, toastCount())
    }

    @Test
    fun dropsConsecutiveDuplicates() {
        host.show(ToastItem("same", ToastType.INFO, 500))
        host.show(ToastItem("same", ToastType.INFO, 500))
        idle(1500)
        assertEquals(0, toastCount())
    }

    @Test
    fun capsQueueSize() {
        repeat(10) { host.show(ToastItem("msg $it", ToastType.INFO, 500)) }
        val seen = linkedSetOf<String>()
        repeat(100) {
            (0 until root.childCount).map { root.getChildAt(it) }.filterIsInstance<ToastView>()
                .forEach { seen += it.findViewById<TextView>(R.id.tvToastMessage).text.toString() }
            idle(100)
        }
        // First one is already showing; the queue keeps only the 3 newest after it.
        assertEquals(listOf("msg 0", "msg 7", "msg 8", "msg 9"), seen.toList())
    }

    @Test
    fun finishingActivityClearsQueue() {
        host.show(ToastItem("a", ToastType.INFO, 500))
        host.show(ToastItem("b", ToastType.INFO, 500))
        assertNotNull(root.getChildAt(root.childCount - 1))
        activity.finish()
        idle(5000)
        assertEquals(0, toastCount())
    }
}
