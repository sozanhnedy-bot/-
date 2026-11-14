package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFilterPresetTest {
    @Test
    fun providesOriginalEditorialFilterChoices() {
        assertEquals(8, VideoFilterPreset.values().size)
        assertEquals("null", VideoFilterPreset.NONE.ffmpegFilter(1f))
        assertEquals("null", VideoFilterPreset.CINEMATIC.ffmpegFilter(0f))
        assertEquals("hue=s=0.500", VideoFilterPreset.BLACK_AND_WHITE.ffmpegFilter(0.5f))
        assertTrue(VideoFilterPreset.WARM.ffmpegFilter(1f).startsWith("colorbalance="))
        assertTrue(VideoFilterPreset.VIVID.ffmpegFilter(0.5f).startsWith("eq="))
    }

    @Test
    fun filterIsAddedAfterCropAndBeforeOutputNormalization() {
        val transform = VideoClipTransform(
            VideoClipTransform.FitMode.FILL,
            1f,
            0.5f,
            0.5f,
            VideoFilterPreset.CINEMATIC,
            0.5f
        )
        val filter = FfmpegVideoExportEngine.buildSegmentVideoFilter(320, 240, transform)

        assertTrue(filter.contains("crop=320:240"))
        assertTrue(filter.contains("eq=contrast=1.050:saturation=0.940"))
        assertTrue(filter.endsWith("setsar=1,fps=30,format=yuv420p"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidFilterIntensity() {
        VideoFilterPreset.WARM.ffmpegFilter(1.1f)
    }
}
