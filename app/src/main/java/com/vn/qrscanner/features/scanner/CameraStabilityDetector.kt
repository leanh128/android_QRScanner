package com.vn.qrscanner.features.scanner

import kotlin.math.abs

/**
 * Tells whether the user is holding the camera still, from the small brightness grids of
 * consecutive frames (see `CameraFrame.lumaGrid`). It works on every camera, fixed-focus ones
 * included, and since refocusing changes the picture too, "stable" also means focus has settled.
 *
 * Grids are compared after removing their average brightness, so auto-exposure or the torch
 * changing the overall light does not count as movement. The frame-to-frame change is smoothed,
 * so one jolt of a shaking hand does not count as moving. Two thresholds give hysteresis: the
 * camera has to get fairly still to become stable, and really move to stop being stable.
 */
class CameraStabilityDetector(
    private val stillThreshold: Float = STILL_THRESHOLD,
    private val moveThreshold: Float = MOVE_THRESHOLD,
    private val driftThreshold: Float = DRIFT_THRESHOLD,
    private val stableDurationMs: Long = STABLE_DURATION_MS,
    private val smoothing: Float = SMOOTHING
) {
    private var previous: FloatArray? = null
    private var stillSince = NOT_STILL

    /** Raw change between the last two frames, for logging. NaN before there are two frames. */
    var lastChange = Float.NaN
        private set

    /** Smoothed change the decisions are based on, for logging. NaN before there are two frames. */
    var smoothedChange = Float.NaN
        private set

    /**
     * Feeds the next frame's grid, taken at [timeMs]. Returns whether the camera has been still
     * for at least [stableDurationMs].
     */
    fun update(grid: FloatArray, timeMs: Long): Boolean {
        val last = previous
        previous = grid
        if (last == null || last.size != grid.size) {
            clearMeasurements()
            return false
        }
        val change = difference(last, grid)
        lastChange = change
        smoothedChange = if (smoothedChange.isNaN()) {
            change
        } else {
            smoothedChange + smoothing * (change - smoothedChange)
        }
        if (smoothedChange > moveThreshold) {
            stillSince = NOT_STILL
        } else if (smoothedChange <= stillThreshold && stillSince == NOT_STILL) {
            stillSince = timeMs
        }
        return stillSince != NOT_STILL && timeMs - stillSince >= stableDurationMs
    }

    /** How long the camera has been still at [timeMs], or -1 if it is not. For logging. */
    fun stillForMs(timeMs: Long): Long = if (stillSince == NOT_STILL) -1 else timeMs - stillSince

    /** How far [grid] is from [anchor], on the same scale as [driftThreshold]. */
    fun driftFrom(anchor: FloatArray, grid: FloatArray): Float =
        if (anchor.size != grid.size) Float.POSITIVE_INFINITY else difference(anchor, grid)

    /**
     * Whether [grid] has wandered away from [anchor], a frame seen earlier. Catches a slow pan
     * whose frame-to-frame changes each stay under the move threshold.
     */
    fun hasDrifted(anchor: FloatArray, grid: FloatArray): Boolean =
        driftFrom(anchor, grid) > driftThreshold

    /** Forgets everything: the camera has to settle again before it counts as stable. */
    fun reset() {
        previous = null
        clearMeasurements()
    }

    private fun clearMeasurements() {
        stillSince = NOT_STILL
        lastChange = Float.NaN
        smoothedChange = Float.NaN
    }

    companion object {
        /** Smoothed per-cell change (0-255 scale) up to which the camera counts as still. */
        const val STILL_THRESHOLD = 9f

        /** Smoothed per-cell change above which the camera counts as moving. */
        const val MOVE_THRESHOLD = 15f

        /** Per-cell change from an anchor frame above which the view has drifted away. */
        const val DRIFT_THRESHOLD = 25f

        /** How long the camera must stay still before decoding starts. */
        const val STABLE_DURATION_MS = 150L

        /** Weight of the newest frame in the smoothed change (1 = no smoothing). */
        const val SMOOTHING = 0.5f

        private const val NOT_STILL = -1L

        /** Mean absolute difference of two grids, each taken relative to its own average. */
        internal fun difference(a: FloatArray, b: FloatArray): Float {
            if (a.isEmpty()) return 0f
            val meanA = a.average().toFloat()
            val meanB = b.average().toFloat()
            var sum = 0f
            for (i in a.indices) {
                sum += abs((a[i] - meanA) - (b[i] - meanB))
            }
            return sum / a.size
        }
    }
}
