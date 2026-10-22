package com.vn.qrscanner.features.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionStabilityDetectorTest {

    private val detector = MotionStabilityDetector(
        stillThreshold = 0.3f,
        moveThreshold = 0.8f,
        stableDurationMs = 200L,
        smoothing = 1f
    )

    /** Feeds a phone lying at rest (gravity on z), plus [shake] added on x every other sample. */
    private fun feed(fromMs: Long, toMs: Long, stepMs: Long = 20L, shake: Float = 0f) {
        var t = fromMs
        var odd = false
        while (t <= toMs) {
            detector.update(if (odd) shake else 0f, 0f, 9.8f, t)
            odd = !odd
            t += stepMs
        }
    }

    @Test
    fun notStableBeforeAnySample() {
        assertFalse(detector.isStable(1000))
    }

    @Test
    fun becomesStableOnlyAfterBeingStillLongEnough() {
        feed(0, 100)
        assertFalse(detector.isStable(100))
        feed(120, 200)
        assertFalse(detector.isStable(200))
        feed(220, 240)
        assertTrue(detector.isStable(240))
    }

    @Test
    fun movementRestartsTheWait() {
        feed(0, 300)
        assertTrue(detector.isStable(300))

        feed(320, 400, shake = 2f)
        assertFalse(detector.isStable(400))

        feed(420, 500)
        assertFalse(detector.isStable(500))
        feed(520, 700)
        assertTrue(detector.isStable(700))
    }

    @Test
    fun smallShakeKeepsAStablePhoneStable() {
        feed(0, 300)
        assertTrue(detector.isStable(300))
        // Between the still and move thresholds: hysteresis keeps the stable state.
        feed(320, 500, shake = 0.5f)
        assertTrue(detector.isStable(500))
    }

    @Test
    fun tiltingSlowlyIsNotMovement() {
        // Gravity slides from z to x over two seconds: tiny change per sample.
        for (i in 0..100) {
            val angle = (Math.PI / 2 * i / 100).toFloat()
            detector.update(9.8f * kotlin.math.sin(angle), 0f, 9.8f * kotlin.math.cos(angle), i * 20L)
        }
        assertTrue(detector.isStable(2000))
    }

    @Test
    fun resetRequiresSettlingAgain() {
        feed(0, 300)
        assertTrue(detector.isStable(300))
        detector.reset()
        assertFalse(detector.isStable(310))
        feed(320, 400)
        assertFalse(detector.isStable(400))
        feed(420, 600)
        assertTrue(detector.isStable(600))
    }

    @Test
    fun smoothingIgnoresASingleJolt() {
        val smoothed = MotionStabilityDetector(0.3f, 0.8f, 200L, smoothing = 0.3f)
        for (t in 0..300 step 20) smoothed.update(0f, 0f, 9.8f, t.toLong())
        assertTrue(smoothed.isStable(300))
        // One jolt out and back: raw change 1.2 each way, smoothed stays under the move threshold.
        smoothed.update(1.2f, 0f, 9.8f, 320)
        smoothed.update(0f, 0f, 9.8f, 340)
        assertTrue(smoothed.isStable(340))
        assertEquals(1.2f, smoothed.lastChange, 0.001f)
    }

    @Test
    fun smoothingStillCatchesRealMovement() {
        val smoothed = MotionStabilityDetector(0.3f, 0.8f, 200L, smoothing = 0.3f)
        for (t in 0..300 step 20) smoothed.update(0f, 0f, 9.8f, t.toLong())
        assertTrue(smoothed.isStable(300))
        for (i in 0 until 6) smoothed.update(if (i % 2 == 0) 3f else 0f, 0f, 9.8f, 320L + i * 20)
        assertFalse(smoothed.isStable(440))
    }
}
