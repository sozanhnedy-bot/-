package com.threadsdocumentarynews.app;

import java.util.Locale;

/** Pure filter-graph builder shared by the Android exporter and host-side tests. */
public final class VideoFilterCommandBuilder {
    private static final int FPS = 30;

    private VideoFilterCommandBuilder() { }

    public static String buildSegmentVideoFilter(int width, int height, VideoClipTransform transform) {
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) {
            throw new IllegalArgumentException("Filter output dimensions must be positive even values.");
        }
        VideoClipTransform value = transform == null ? VideoClipTransform.defaultTransform() : transform;
        int scaledWidth = even(Math.round(width * value.getZoom()));
        int scaledHeight = even(Math.round(height * value.getZoom()));
        boolean fit = value.getFitMode() == VideoClipTransform.FitMode.FIT;
        StringBuilder filter = new StringBuilder("scale=")
                .append(scaledWidth).append(':').append(scaledHeight)
                .append(":force_original_aspect_ratio=").append(fit ? "decrease" : "increase");
        if (fit) {
            filter.append(",pad=").append(scaledWidth).append(':').append(scaledHeight)
                    .append(":(ow-iw)/2:(oh-ih)/2:black");
        }
        filter.append(",crop=").append(width).append(':').append(height)
                .append(":(in_w-out_w)*").append(decimal(value.getPositionX()))
                .append(":(in_h-out_h)*").append(decimal(value.getPositionY()));
        String colorFilter = value.getFilterPreset().ffmpegFilter(value.getFilterIntensity());
        if (!"null".equals(colorFilter)) filter.append(',').append(colorFilter);
        filter.append(",setsar=1,fps=").append(FPS).append(",format=yuv420p");
        return filter.toString();
    }

    private static int even(int value) { return value % 2 == 0 ? value : value + 1; }

    private static String decimal(float value) { return String.format(Locale.US, "%.3f", value); }
}
