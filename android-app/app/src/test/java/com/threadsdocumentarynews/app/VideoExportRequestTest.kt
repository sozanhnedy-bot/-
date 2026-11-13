package com.threadsdocumentarynews.app

import org.junit.Assert.assertFalse
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
}
