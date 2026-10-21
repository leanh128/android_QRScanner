package com.vn.qrscanner.features.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStabilityDetectorTest {

    private val detector = CameraStabilityDetector(
        stillThreshold = 3f,
        moveThreshold = 8f,
        driftThreshold = 12f,
        stableDurationMs = 300L,
        smoothing = 1f
    )

    /** A gradient grid; [shift] moves the pattern sideways, like panning the camera. */
    private fun grid(shift: Int = 0, brightness: Float = 0f) =
        FloatArray(64) { i -> ((i + shift) % 64) * 4f + brightness }

    @Test
    fun becomesStableOnlyAfterBeingStillLongEnough() {
        assertFalse(detector.update(grid(), 0))
        assertFalse(detector.update(grid(), 100))
        assertFalse(detector.update(grid(), 200))
        assertTrue(detector.update(grid(), 400))
    }

    @Test
    fun movementRestartsTheWait() {
        detector.update(grid(), 0)
        detector.update(grid(), 100)
        assertTrue(detector.update(grid(), 500))

        assertFalse(detector.update(grid(shift = 8), 550))
        assertFalse(detector.update(grid(shift = 8), 600))
        assertFalse(detector.update(grid(shift = 8), 800))
        assertTrue(detector.update(grid(shift = 8), 950))
    }

    @Test
    fun overallBrightnessChangeIsNotMovement() {
        detector.update(grid(), 0)
        detector.update(grid(), 100)
        assertTrue(detector.update(grid(brightness = 40f), 400))
    }

    @Test
    fun smallChangesKeepAStableCameraStable() {
        detector.update(grid(), 0)
        detector.update(grid(), 100)
        assertTrue(detector.update(grid(), 400))
        // Between the still and move thresholds: hysteresis keeps the stable state.
        val jitter = grid().also { g -> for (i in g.indices step 2) g[i] += 10f }
        assertTrue(detector.update(jitter, 450))
    }

    @Test
    fun detectsDriftFromAnchor() {
        assertFalse(detector.hasDrifted(grid(), grid(brightness = 20f)))
        assertTrue(detector.hasDrifted(grid(), grid(shift = 16)))
        assertTrue(detector.hasDrifted(grid(), FloatArray(10)))
    }

    @Test
    fun resetRequiresSettlingAgain() {
        detector.update(grid(), 0)
        detector.update(grid(), 100)
        assertTrue(detector.update(grid(), 400))
        detector.reset()
        assertFalse(detector.update(grid(), 450))
        assertFalse(detector.update(grid(), 500))
    }

    @Test
    fun smoothingIgnoresASingleJolt() {
        val smoothed = CameraStabilityDetector(3f, 8f, 12f, 300L, smoothing = 0.5f)
        smoothed.update(grid(), 0)
        smoothed.update(grid(), 100)
        assertTrue(smoothed.update(grid(), 400))
        // One shaky frame: raw change 10 on the way out and back, above the move threshold.
        val jolt = grid().also { g -> for (i in g.indices step 2) g[i] += 20f }
        assertTrue(smoothed.update(jolt, 433))
        assertTrue(smoothed.update(grid(), 466))
        assertEquals(10f, smoothed.lastChange, 0.001f)
    }

    @Test
    fun smoothingStillCatchesRealMovement() {
        val smoothed = CameraStabilityDetector(3f, 8f, 12f, 300L, smoothing = 0.5f)
        smoothed.update(grid(), 0)
        smoothed.update(grid(), 100)
        assertTrue(smoothed.update(grid(), 400))
        assertFalse(smoothed.update(grid(shift = 8), 433))
    }

    @Test
    fun defaultsTolerateHandShake() {
        val defaults = CameraStabilityDetector()
        defaults.update(grid(), 0)
        // Tremor-sized changes (raw 5 per frame) are still enough to become stable.
        val tremor = grid().also { g -> for (i in g.indices step 2) g[i] += 10f }
        defaults.update(tremor, 33)
        defaults.update(grid(), 66)
        defaults.update(tremor, 100)
        defaults.update(grid(), 200)
        assertTrue(defaults.update(tremor, 350))
    }

    @Test
    fun differenceIgnoresMean() {
        assertEquals(0f, CameraStabilityDetector.difference(grid(), grid(brightness = 50f)), 0.001f)
    }
}
