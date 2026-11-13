package com.threadsdocumentarynews.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExportRequestTest {
    @Test
    fun acceptsAllFourWorkspaceColorPalettes() {
        for (palette in 0..3) assertTrue(VideoExportRequest.isValidColorPalette(palette))
    }

    @Test
    fun rejectsPaletteIndexesOutsideWorkspaceOptions() {
        assertFalse(VideoExportRequest.isValidColorPalette(-1))
        assertFalse(VideoExportRequest.isValidColorPalette(4))
    }

    @Test
    fun imageInputUsesGenericStreamLoopForGifPngAndJpegDemuxers() {
        val options = FfmpegVideoExportEngine.imageInputOptions("/cache/news image.gif", 5000L)

        assertEquals("-stream_loop -1 -i '/cache/news image.gif' -t 5.000", options)
        assertFalse(options.contains("-loop 1"))
        assertFalse(options.contains("-framerate"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun imageInputRejectsNonPositiveDuration() {
        FfmpegVideoExportEngine.imageInputOptions("/cache/photo.png", 0L)
    }
}
