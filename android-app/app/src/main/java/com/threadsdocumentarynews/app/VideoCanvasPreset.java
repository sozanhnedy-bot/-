package com.threadsdocumentarynews.app;

/** Output-canvas presets for common portrait, landscape and social-video layouts. */
public enum VideoCanvasPreset {
    PORTRAIT("عمودي — 9:16", 9, 16),
    LANDSCAPE("أفقي — 16:9", 16, 9),
    INSTAGRAM_FEED("Instagram Feed — 4:5", 4, 5),
    SQUARE("مربع — 1:1", 1, 1),
    INSTAGRAM_REELS("Instagram Reels / Stories — 9:16", 9, 16),
    YOUTUBE_SHORTS("YouTube Shorts — 9:16", 9, 16);

    private final String label;
    private final int ratioWidth;
    private final int ratioHeight;

    VideoCanvasPreset(String label, int ratioWidth, int ratioHeight) {
        this.label = label;
        this.ratioWidth = ratioWidth;
        this.ratioHeight = ratioHeight;
    }

    public String getLabel() { return label; }
    public int getRatioWidth() { return ratioWidth; }
    public int getRatioHeight() { return ratioHeight; }
    public float getAspectRatio() { return (float) ratioWidth / ratioHeight; }

    /**
     * Returns even dimensions suitable for yuv420p. The 720p/1080p setting is
     * applied to the shorter canvas side to preserve each preset's aspect ratio.
     */
    public int[] outputSize(boolean fullHd) {
        int shortSide = fullHd ? 1080 : 720;
        int width;
        int height;
        if (ratioWidth < ratioHeight) {
            width = shortSide;
            height = Math.round(shortSide * (float) ratioHeight / ratioWidth);
        } else if (ratioWidth > ratioHeight) {
            height = shortSide;
            width = Math.round(shortSide * (float) ratioWidth / ratioHeight);
        } else {
            width = shortSide;
            height = shortSide;
        }
        return new int[]{even(width), even(height)};
    }

    public static String[] labels() {
        VideoCanvasPreset[] presets = values();
        String[] labels = new String[presets.length];
        for (int i = 0; i < presets.length; i++) labels[i] = presets[i].label;
        return labels;
    }

    public static VideoCanvasPreset fromPosition(int position) {
        VideoCanvasPreset[] presets = values();
        if (position < 0 || position >= presets.length) return PORTRAIT;
        return presets[position];
    }

    private static int even(int value) {
        return value % 2 == 0 ? value : value + 1;
    }
}
