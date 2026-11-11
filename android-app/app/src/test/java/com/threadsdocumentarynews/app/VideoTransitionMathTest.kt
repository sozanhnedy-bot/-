package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTransitionMathTest {
    @Test
    fun fadeIsOpaqueAtClipStartAndTransparentAfterFadeIn() {
        assertEquals(255, VideoTransitionMath.blackAlphaAt(0L, 5_000_000L))
        assertEquals(0, VideoTransitionMath.blackAlphaAt(450_000L, 5_000_000L))
    }

    @Test
    fun fadeOutStartsAtClipEndAndIsOpaqueAtTheLastFrame() {
        assertEquals(0, VideoTransitionMath.blackAlphaAt(4_550_000L, 5_000_000L))
        assertEquals(255, VideoTransitionMath.blackAlphaAt(5_000_000L, 5_000_000L))
    }

    @Test
    fun clipsShorterThanTwiceTheFadeCombineBothEdgeFades() {
        assertEquals(128, VideoTransitionMath.blackAlphaAt(225_000L, 450_000L))
    }

    @Test
    fun tickerMovesAndWrapsWithinItsRepeatedPattern() {
        assertEquals(0f, VideoTransitionMath.tickerOffset(0L, 400f, 100f), 0.01f)
        assertEquals(100f, VideoTransitionMath.tickerOffset(1_000_000L, 400f, 100f), 0.01f)
        assertEquals(0f, VideoTransitionMath.tickerOffset(4_000_000L, 400f, 100f), 0.01f)
    }

    @Test
    fun musicBedFadesInAndOutAtTheSelectedBackgroundLevel() {
        assertEquals(0f, VideoTransitionMath.musicGainAt(0L, 5_000_000L, 0.18f), 0.001f)
        assertEquals(0.18f, VideoTransitionMath.musicGainAt(1_000_000L, 5_000_000L, 0.18f), 0.001f)
        assertEquals(0f, VideoTransitionMath.musicGainAt(5_000_000L, 5_000_000L, 0.18f), 0.001f)
    }

    @Test
    fun exportRequestRejectsNonFiniteMusicGain() {
        assertTrue(VideoExportRequest.isValidMusicGain(0f))
        assertTrue(VideoExportRequest.isValidMusicGain(1f))
        assertFalse(VideoExportRequest.isValidMusicGain(Float.NaN))
        assertFalse(VideoExportRequest.isValidMusicGain(Float.POSITIVE_INFINITY))
        assertFalse(VideoExportRequest.isValidMusicGain(-0.01f))
        assertFalse(VideoExportRequest.isValidMusicGain(1.01f))
    }

    @Test
    fun exportRequestRequiresEvenPositiveYuv420pDimensions() {
        assertTrue(VideoExportRequest.isValidOutputSize(1080, 1920))
        assertTrue(VideoExportRequest.isValidOutputSize(1280, 720))
        assertFalse(VideoExportRequest.isValidOutputSize(0, 1080))
        assertFalse(VideoExportRequest.isValidOutputSize(1081, 1920))
        assertFalse(VideoExportRequest.isValidOutputSize(1280, 721))
    }
}
