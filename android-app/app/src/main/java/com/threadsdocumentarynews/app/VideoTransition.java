package com.threadsdocumentarynews.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pure configuration and timing helpers for cuts and fade-to-black clip boundaries. */
public final class VideoTransition {
    public enum Mode { NONE, FADE_TO_BLACK }

    public static final long DEFAULT_DURATION_MS = 500L;
    private static final long[] SUPPORTED_DURATIONS_MS = {250L, 500L, 750L};

    private VideoTransition() { }

    public static boolean isValidDurationMs(long durationMs) {
        for (long supported : SUPPORTED_DURATIONS_MS) if (durationMs == supported) return true;
        return false;
    }

    /** A boundary uses one duration that never exceeds half of either adjoining clip. */
    public static long boundaryDurationMs(Mode mode, int clipCount, long requestedDurationMs,
            long previousClipDurationMs, long nextClipDurationMs) {
        if (mode != Mode.FADE_TO_BLACK || clipCount <= 1 || requestedDurationMs <= 0L
                || previousClipDurationMs <= 0L || nextClipDurationMs <= 0L) return 0L;
        long maximum = Math.min(previousClipDurationMs / 2L, nextClipDurationMs / 2L);
        return Math.min(requestedDurationMs, maximum);
    }

    /** Returns fade filters positioned on the concatenated timeline; audio duration is unchanged. */
    public static List<String> timelineFadeFilters(Mode mode, long requestedDurationMs,
            List<Long> clipDurationsMs) {
        if (mode != Mode.FADE_TO_BLACK || clipDurationsMs == null || clipDurationsMs.size() <= 1) {
            return Collections.emptyList();
        }
        ArrayList<String> filters = new ArrayList<>();
        long boundaryMs = 0L;
        for (int i = 0; i < clipDurationsMs.size() - 1; i++) {
            long previousMs = clipDurationsMs.get(i) == null ? 0L : clipDurationsMs.get(i);
            long nextMs = clipDurationsMs.get(i + 1) == null ? 0L : clipDurationsMs.get(i + 1);
            long fadeMs = boundaryDurationMs(mode, clipDurationsMs.size(), requestedDurationMs,
                    previousMs, nextMs);
            boundaryMs += previousMs;
            if (fadeMs > 0L) {
                long fadeOutStartMs = boundaryMs - fadeMs;
                filters.add(String.format(Locale.US,
                        "fade=t=out:st=%.3f:d=%.3f:color=black", fadeOutStartMs / 1000.0, fadeMs / 1000.0));
                filters.add(String.format(Locale.US,
                        "fade=t=in:st=%.3f:d=%.3f:color=black", boundaryMs / 1000.0, fadeMs / 1000.0));
            }
        }
        return Collections.unmodifiableList(filters);
    }
}
