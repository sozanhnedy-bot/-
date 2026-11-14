package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTransitionTest {
    @Test
    fun defaultNoneProducesNoFiltersAndSingleClipIgnoresFade() {
        assertTrue(VideoTransition.timelineFadeFilters(VideoTransition.Mode.NONE, 500L, listOf(2_000L, 2_000L)).isEmpty())
        assertTrue(VideoTransition.timelineFadeFilters(VideoTransition.Mode.FADE_TO_BLACK, 500L, listOf(2_000L)).isEmpty())
    }

    @Test
    fun boundaryDurationIsCappedAtHalfOfTheShorterAdjacentClip() {
        assertEquals(125L, VideoTransition.boundaryDurationMs(
            VideoTransition.Mode.FADE_TO_BLACK, 2, 750L, 250L, 4_000L))
        assertEquals(250L, VideoTransition.boundaryDurationMs(
            VideoTransition.Mode.FADE_TO_BLACK, 2, 750L, 2_000L, 500L))
    }

    @Test
    fun adjacentFiltersFadeOutPreviousEndAndFadeInNextStartWithoutChangingTimeline() {
        val filters = VideoTransition.timelineFadeFilters(
            VideoTransition.Mode.FADE_TO_BLACK, 750L, listOf(2_000L, 3_000L, 2_000L))
        assertEquals(4, filters.size)
        assertEquals("fade=t=out:st=1.250:d=0.750:color=black", filters[0])
        assertEquals("fade=t=in:st=2.000:d=0.750:color=black", filters[1])
        assertEquals("fade=t=out:st=4.250:d=0.750:color=black", filters[2])
        assertEquals("fade=t=in:st=5.000:d=0.750:color=black", filters[3])
    }

    @Test
    fun veryShortAdjacentClipCanReduceDurationAndInvalidDurationsAreRejected() {
        assertEquals(listOf(
            "fade=t=out:st=0.875:d=0.125:color=black",
            "fade=t=in:st=1.000:d=0.125:color=black"),
            VideoTransition.timelineFadeFilters(VideoTransition.Mode.FADE_TO_BLACK, 750L,
                listOf(1_000L, 250L)))
        assertTrue(VideoTransition.isValidDurationMs(250L))
        assertTrue(VideoTransition.isValidDurationMs(500L))
        assertTrue(VideoTransition.isValidDurationMs(750L))
        assertTrue(!VideoTransition.isValidDurationMs(300L))
    }
}
