package com.threadsdocumentarynews.app

import kotlin.math.max
import kotlin.math.roundToInt

/** Pure timing helpers for per-scene transitions and a continuously moving news ticker. */
internal object VideoTransitionMath {
    const val DEFAULT_FADE_DURATION_US = 450_000L

    /** Returns the black overlay alpha: opaque at each edge, transparent between the fade regions. */
    @JvmStatic
    @JvmOverloads
    fun blackAlphaAt(timeUs: Long, durationUs: Long, fadeDurationUs: Long = DEFAULT_FADE_DURATION_US): Int {
        if (timeUs < 0L || fadeDurationUs <= 0L) return 0
        val fadeIn = (1.0 - timeUs.toDouble() / fadeDurationUs.toDouble()).coerceIn(0.0, 1.0)
        val fadeOut = if (durationUs > 0L) {
            ((timeUs - (durationUs - fadeDurationUs)).toDouble() / fadeDurationUs.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0
        return (max(fadeIn, fadeOut) * 255.0).roundToInt().coerceIn(0, 255)
    }

    /** Distance in pixels for a repeating ticker marquee, moving right-to-left. */
    @JvmStatic
    fun tickerOffset(timeUs: Long, textWidthPx: Float, speedPxPerSecond: Float): Float {
        if (timeUs <= 0L || textWidthPx <= 0f || speedPxPerSecond <= 0f) return 0f
        val distance = timeUs.toDouble() / 1_000_000.0 * speedPxPerSecond.toDouble()
        return (distance % textWidthPx.toDouble()).toFloat()
    }

    /** Applies a short fade at both ends while keeping the bed below the narration level. */
    @JvmStatic
    @JvmOverloads
    fun musicGainAt(timeUs: Long, durationUs: Long, baseGain: Float, fadeDurationUs: Long = DEFAULT_FADE_DURATION_US): Float {
        if (baseGain <= 0f || fadeDurationUs <= 0L || timeUs < 0L) return 0f
        val fadeIn = (timeUs.toDouble() / fadeDurationUs.toDouble()).coerceIn(0.0, 1.0)
        val fadeOut = if (durationUs > 0L) {
            ((durationUs - timeUs).toDouble() / fadeDurationUs.toDouble()).coerceIn(0.0, 1.0)
        } else 1.0
        return (baseGain * minOf(fadeIn, fadeOut)).toFloat()
    }
}
