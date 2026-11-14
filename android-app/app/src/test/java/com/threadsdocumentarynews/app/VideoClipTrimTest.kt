package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoClipTrimTest {
    @Test
    fun defaultTransformKeepsTheWholeClip() {
        val transform = VideoClipTransform.defaultTransform()
        assertEquals(0f, transform.trimStartFraction, 0f)
        assertEquals(1f, transform.trimEndFraction, 0f)
    }

    @Test
    fun normalizedTrimRangeMapsToMillisecondPositions() {
        val transform = VideoClipTransform(
            VideoClipTransform.FitMode.FILL,
            1f,
            0.5f,
            0.5f,
            VideoFilterPreset.NONE,
            1f,
            0.2f,
            0.8f
        )
        assertEquals(12_000L, transform.getTrimStartMs(60_000L))
        assertEquals(48_000L, transform.getTrimEndMs(60_000L))
    }

    @Test
    fun ffmpegSeekOptionIsPlacedBeforeInputForAccurateSynchronizedTrim() {
        val options = FfmpegVideoExportEngine.seekInputOptions("/cache/clip.mp4", 1_250L)
        assertEquals("-ss 1.250 -i '/cache/clip.mp4'", options)
        assertTrue(options.indexOf("-ss") < options.indexOf("-i"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyOrReversedTrimRange() {
        VideoClipTransform(
            VideoClipTransform.FitMode.FILL,
            1f,
            0.5f,
            0.5f,
            VideoFilterPreset.NONE,
            1f,
            0.7f,
            0.7f
        )
    }
}
