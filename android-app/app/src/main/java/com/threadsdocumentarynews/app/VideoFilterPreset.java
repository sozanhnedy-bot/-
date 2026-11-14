package com.threadsdocumentarynews.app;

import java.util.Locale;

/** Original, restrained color treatments for editorial footage. */
public enum VideoFilterPreset {
    NONE("بدون فلتر"),
    NEWS_NEUTRAL("إخباري محايد"),
    CINEMATIC("سينمائي هادئ"),
    WARM("دافئ"),
    COOL("بارد"),
    BLACK_AND_WHITE("أبيض وأسود"),
    VIVID("ألوان زاهية"),
    SOFT("ناعم منخفض التباين");

    private final String label;

    VideoFilterPreset(String label) { this.label = label; }

    public String getLabel() { return label; }

    public String ffmpegFilter(float intensity) {
        if (Float.isNaN(intensity) || Float.isInfinite(intensity) || intensity < 0f || intensity > 1f) {
            throw new IllegalArgumentException("Filter intensity must be between 0 and 1.");
        }
        if (this == NONE || intensity == 0f) return "null";
        switch (this) {
            case NEWS_NEUTRAL:
                return String.format(Locale.US, "eq=contrast=%.3f:saturation=%.3f",
                        mix(1f, 1.04f, intensity), mix(1f, 0.98f, intensity));
            case CINEMATIC:
                return String.format(Locale.US, "eq=contrast=%.3f:saturation=%.3f",
                        mix(1f, 1.10f, intensity), mix(1f, 0.88f, intensity));
            case WARM:
                return String.format(Locale.US, "colorbalance=rs=%.3f:gs=%.3f:bs=%.3f",
                        0.12f * intensity, 0.04f * intensity, -0.12f * intensity);
            case COOL:
                return String.format(Locale.US, "colorbalance=rs=%.3f:gs=%.3f:bs=%.3f",
                        -0.10f * intensity, 0.02f * intensity, 0.12f * intensity);
            case BLACK_AND_WHITE:
                return String.format(Locale.US, "hue=s=%.3f", 1f - intensity);
            case VIVID:
                return String.format(Locale.US, "eq=contrast=%.3f:saturation=%.3f",
                        mix(1f, 1.05f, intensity), mix(1f, 1.25f, intensity));
            case SOFT:
                return String.format(Locale.US, "eq=contrast=%.3f:saturation=%.3f:brightness=%.3f",
                        mix(1f, 0.90f, intensity), mix(1f, 0.85f, intensity), 0.02f * intensity);
            default:
                return "null";
        }
    }

    public static String[] labels() {
        VideoFilterPreset[] presets = values();
        String[] labels = new String[presets.length];
        for (int i = 0; i < presets.length; i++) labels[i] = presets[i].label;
        return labels;
    }

    public static VideoFilterPreset fromPosition(int position) {
        VideoFilterPreset[] presets = values();
        if (position < 0 || position >= presets.length) return NONE;
        return presets[position];
    }

    private static float mix(float start, float end, float amount) {
        return start + (end - start) * amount;
    }
}
