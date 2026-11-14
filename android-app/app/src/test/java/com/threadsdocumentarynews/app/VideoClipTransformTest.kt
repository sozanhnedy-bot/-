package com.threadsdocumentarynews.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoClipTransformTest {
    @Test
    fun fillFilterUsesScaledCanvasAndCropPositionWithoutPadding() {
        val transform = VideoClipTransform(VideoClipTransform.FitMode.FILL, 1.5f, 0f, 1f)
        val filter = FfmpegVideoExportEngine.buildSegmentVideoFilter(320, 240, transform)

        assertTrue(filter.startsWith("scale=480:360:force_original_aspect_ratio=increase"))
        assertTrue(filter.contains("crop=320:240:(in_w-out_w)*0.000:(in_h-out_h)*1.000"))
        assertFalse(filter.contains("pad="))
        assertTrue(filter.endsWith("setsar=1,fps=30,format=yuv420p"))
    }

    @Test
    fun fitFilterPadsBeforeCropAndKeepsRequestedOutputDimensions() {
        val transform = VideoClipTransform(VideoClipTransform.FitMode.FIT, 1f, 0.5f, 0.5f)
        val filter = FfmpegVideoExportEngine.buildSegmentVideoFilter(320, 240, transform)

        assertTrue(filter.startsWith("scale=320:240:force_original_aspect_ratio=decrease"))
        assertTrue(filter.contains("pad=320:240:(ow-iw)/2:(oh-ih)/2:black"))
        assertTrue(filter.contains("crop=320:240:(in_w-out_w)*0.500:(in_h-out_h)*0.500"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZoomAboveSupportedRange() {
        VideoClipTransform(VideoClipTransform.FitMode.FILL, 2.6f, 0.5f, 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCropPositionOutsideNormalizedRange() {
        VideoClipTransform(VideoClipTransform.FitMode.FILL, 1f, 1.1f, 0.5f)
    }
}
