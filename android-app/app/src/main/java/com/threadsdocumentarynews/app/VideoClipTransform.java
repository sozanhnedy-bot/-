package com.threadsdocumentarynews.app;

/** Non-destructive per-clip framing settings for the project canvas. */
public final class VideoClipTransform {
    public enum FitMode { FIT, FILL }

    private final FitMode fitMode;
    private final float zoom;
    private final float positionX;
    private final float positionY;
    private final VideoFilterPreset filterPreset;
    private final float filterIntensity;
    private final float trimStartFraction;
    private final float trimEndFraction;

    public VideoClipTransform(FitMode fitMode, float zoom, float positionX, float positionY) {
        this(fitMode, zoom, positionX, positionY, VideoFilterPreset.NONE, 1f);
    }

    public VideoClipTransform(FitMode fitMode, float zoom, float positionX, float positionY,
            VideoFilterPreset filterPreset, float filterIntensity) {
        this(fitMode, zoom, positionX, positionY, filterPreset, filterIntensity, 0f, 1f);
    }

    public VideoClipTransform(FitMode fitMode, float zoom, float positionX, float positionY,
            VideoFilterPreset filterPreset, float filterIntensity,
            float trimStartFraction, float trimEndFraction) {
        if (fitMode == null) throw new IllegalArgumentException("A fit mode is required.");
        if (filterPreset == null) throw new IllegalArgumentException("A filter preset is required.");
        if (Float.isNaN(zoom) || Float.isInfinite(zoom) || zoom < 1f || zoom > 2.5f) {
            throw new IllegalArgumentException("Zoom must be between 1.0 and 2.5.");
        }
        if (!isNormalized(positionX) || !isNormalized(positionY)) {
            throw new IllegalArgumentException("Crop positions must be between 0 and 1.");
        }
        if (!isNormalized(filterIntensity)) {
            throw new IllegalArgumentException("Filter intensity must be between 0 and 1.");
        }
        if (!isNormalized(trimStartFraction) || !isNormalized(trimEndFraction)
                || trimStartFraction >= trimEndFraction) {
            throw new IllegalArgumentException("Trim range must be normalized and end after start.");
        }
        this.fitMode = fitMode;
        this.zoom = zoom;
        this.positionX = positionX;
        this.positionY = positionY;
        this.filterPreset = filterPreset;
        this.filterIntensity = filterIntensity;
        this.trimStartFraction = trimStartFraction;
        this.trimEndFraction = trimEndFraction;
    }

    public static VideoClipTransform defaultTransform() {
        return new VideoClipTransform(FitMode.FILL, 1f, 0.5f, 0.5f);
    }

    private static boolean isNormalized(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && value >= 0f && value <= 1f;
    }

    public FitMode getFitMode() { return fitMode; }
    public float getZoom() { return zoom; }
    public float getPositionX() { return positionX; }
    public float getPositionY() { return positionY; }
    public VideoFilterPreset getFilterPreset() { return filterPreset; }
    public float getFilterIntensity() { return filterIntensity; }
    public float getTrimStartFraction() { return trimStartFraction; }
    public float getTrimEndFraction() { return trimEndFraction; }

    public long getTrimStartMs(long sourceDurationMs) {
        if (sourceDurationMs <= 0L) throw new IllegalArgumentException("Source duration must be positive.");
        return Math.round(sourceDurationMs * trimStartFraction);
    }

    public long getTrimEndMs(long sourceDurationMs) {
        if (sourceDurationMs <= 0L) throw new IllegalArgumentException("Source duration must be positive.");
        return Math.round(sourceDurationMs * trimEndFraction);
    }
}
