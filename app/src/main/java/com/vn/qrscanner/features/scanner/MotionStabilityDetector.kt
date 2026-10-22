package com.vn.qrscanner.features.scanner

import kotlin.math.sqrt

/**
 * Tells whether the user is holding the phone still, from accelerometer samples (m/s², any
 * orientation). No permission is needed for it.
 *
 * Gravity is the same in two consecutive samples while the phone is still or tilting slowly, so
 * the size of the difference between them is a measure of how much the phone is being shaken or
 * moved. It is smoothed, so one jolt of a shaking hand does not count as moving. Two thresholds
 * give hysteresis: the phone has to get fairly still to become stable, and really move to stop
 * being stable.
 *
 * An accelerometer only senses changes of speed: a slow pan at constant speed is not seen.
 */
class MotionStabilityDetector(
    private val stillThreshold: Float = STILL_THRESHOLD,
    private val moveThreshold: Float = MOVE_THRESHOLD,
    private val stableDurationMs: Long = STABLE_DURATION_MS,
    private val smoothing: Float = SMOOTHING
) {
    private var hasPrevious = false
    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f
    private var stillSince = NOT_STILL

    /** Raw change between the last two samples, for logging. NaN before there are two samples. */
    var lastChange = Float.NaN
        private set

    /** Smoothed change the decisions are based on, for logging. NaN before there are two samples. */
    var smoothedChange = Float.NaN
        private set

    /** Feeds the next accelerometer sample, taken at [timeMs]. */
    fun update(x: Float, y: Float, z: Float, timeMs: Long) {
        if (!hasPrevious) {
            hasPrevious = true
        } else {
            val dx = x - prevX
            val dy = y - prevY
            val dz = z - prevZ
            val change = sqrt(dx * dx + dy * dy + dz * dz)
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
        }
        prevX = x
        prevY = y
        prevZ = z
    }

    /** Whether the phone has been still for at least [stableDurationMs] at [timeMs]. */
    fun isStable(timeMs: Long): Boolean = stillSince != NOT_STILL && timeMs - stillSince >= stableDurationMs

    /** How long the phone has been still at [timeMs], or -1 if it is not. For logging. */
    fun stillForMs(timeMs: Long): Long = if (stillSince == NOT_STILL) -1 else timeMs - stillSince

    /** Forgets everything: the phone has to settle again before it counts as stable. */
    fun reset() {
        hasPrevious = false
        stillSince = NOT_STILL
        lastChange = Float.NaN
        smoothedChange = Float.NaN
    }

    companion object {
        /** Smoothed change between samples (m/s²) up to which the phone counts as still. */
        const val STILL_THRESHOLD = 0.3f

        /** Smoothed change between samples (m/s²) above which the phone counts as moving. */
        const val MOVE_THRESHOLD = 0.8f

        /** How long the phone must stay still before decoding starts. */
        const val STABLE_DURATION_MS = 80L

        /** Weight of the newest sample in the smoothed change (1 = no smoothing). */
        const val SMOOTHING = 0.3f

        private const val NOT_STILL = -1L
    }
}
