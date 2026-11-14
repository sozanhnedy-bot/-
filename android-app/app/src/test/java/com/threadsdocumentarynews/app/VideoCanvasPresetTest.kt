package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCanvasPresetTest {
    @Test
    fun offersPortraitLandscapeInstagramFeedSquareReelsAndShortsPresets() {
        assertEquals(6, VideoCanvasPreset.values().size)
        assertEquals("عمودي — 9:16", VideoCanvasPreset.fromPosition(0).label)
        assertEquals(16f / 9f, VideoCanvasPreset.fromPosition(1).aspectRatio, 0.0001f)
        assertEquals(4f / 5f, VideoCanvasPreset.fromPosition(2).aspectRatio, 0.0001f)
        assertEquals(1f, VideoCanvasPreset.fromPosition(3).aspectRatio, 0.0001f)
        assertEquals(9f / 16f, VideoCanvasPreset.fromPosition(4).aspectRatio, 0.0001f)
        assertEquals(9f / 16f, VideoCanvasPreset.fromPosition(5).aspectRatio, 0.0001f)
    }

    @Test
    fun outputSizesMatchPresetRatiosAndAreEvenAtBothResolutions() {
        for (preset in VideoCanvasPreset.values()) {
            for (fullHd in listOf(false, true)) {
                val size = preset.outputSize(fullHd)
                assertTrue(size[0] > 0 && size[1] > 0)
                assertEquals(0, size[0] % 2)
                assertEquals(0, size[1] % 2)
                assertEquals(preset.aspectRatio, size[0].toFloat() / size[1], 0.002f)
            }
        }
        assertEquals(720, VideoCanvasPreset.PORTRAIT.outputSize(false)[0])
        assertEquals(1280, VideoCanvasPreset.PORTRAIT.outputSize(false)[1])
        assertEquals(1080, VideoCanvasPreset.INSTAGRAM_FEED.outputSize(true)[0])
        assertEquals(1350, VideoCanvasPreset.INSTAGRAM_FEED.outputSize(true)[1])
        assertEquals(1920, VideoCanvasPreset.LANDSCAPE.outputSize(true)[0])
        assertEquals(1080, VideoCanvasPreset.LANDSCAPE.outputSize(true)[1])
    }

    @Test
    fun invalidSpinnerPositionsFallBackToPortrait() {
        assertEquals(VideoCanvasPreset.PORTRAIT, VideoCanvasPreset.fromPosition(-1))
        assertEquals(VideoCanvasPreset.PORTRAIT, VideoCanvasPreset.fromPosition(99))
    }
}
