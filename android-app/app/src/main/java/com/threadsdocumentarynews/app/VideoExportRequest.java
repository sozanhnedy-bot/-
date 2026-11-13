package com.threadsdocumentarynews.app;

import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable input for a local video export. */
public final class VideoExportRequest {
    private final List<Uri> mediaUris;
    private final Uri narrationUri;
    private final Uri musicUri;
    private final String headline;
    private final String lowerThird;
    private final String ticker;
    private final File outputFile;
    private final int width;
    private final int height;
    private final int imageDurationSeconds;
    private final float musicGain;
    private final boolean breakingNews;
    private final int colorPalette;

    public VideoExportRequest(List<Uri> mediaUris, Uri narrationUri, Uri musicUri,
            String headline, String lowerThird, String ticker, File outputFile,
            int width, int height, int imageDurationSeconds, float musicGain) {
        this(mediaUris, narrationUri, musicUri, headline, lowerThird, ticker, outputFile,
                width, height, imageDurationSeconds, musicGain, true, 0);
    }

    public VideoExportRequest(List<Uri> mediaUris, Uri narrationUri, Uri musicUri,
            String headline, String lowerThird, String ticker, File outputFile,
            int width, int height, int imageDurationSeconds, float musicGain,
            boolean breakingNews) {
        this(mediaUris, narrationUri, musicUri, headline, lowerThird, ticker, outputFile,
                width, height, imageDurationSeconds, musicGain, breakingNews, 0);
    }

    public VideoExportRequest(List<Uri> mediaUris, Uri narrationUri, Uri musicUri,
            String headline, String lowerThird, String ticker, File outputFile,
            int width, int height, int imageDurationSeconds, float musicGain,
            boolean breakingNews, int colorPalette) {
        if (mediaUris == null || mediaUris.isEmpty()) {
            throw new IllegalArgumentException("At least one image or video is required.");
        }
        if (outputFile == null) throw new IllegalArgumentException("An output file is required.");
        if (!isValidOutputSize(width, height)) {
            throw new IllegalArgumentException("Output dimensions must be positive, even, and suitable for yuv420p.");
        }
        if (imageDurationSeconds <= 0) throw new IllegalArgumentException("Image duration must be positive.");
        if (!isValidMusicGain(musicGain)) {
            throw new IllegalArgumentException("Music gain must be finite and between 0 and 1.");
        }
        if (!isValidColorPalette(colorPalette)) {
            throw new IllegalArgumentException("Color palette must be between 0 and 3.");
        }

        ArrayList<Uri> copy = new ArrayList<>(mediaUris.size());
        for (Uri uri : mediaUris) {
            if (uri == null) throw new IllegalArgumentException("Media list cannot contain a null URI.");
            copy.add(uri);
        }
        this.mediaUris = Collections.unmodifiableList(copy);
        this.narrationUri = narrationUri;
        this.musicUri = musicUri;
        this.headline = headline == null ? "" : headline;
        this.lowerThird = lowerThird == null ? "" : lowerThird;
        this.ticker = ticker == null ? "" : ticker;
        this.outputFile = outputFile;
        this.width = width;
        this.height = height;
        this.imageDurationSeconds = imageDurationSeconds;
        this.musicGain = musicGain;
        this.breakingNews = breakingNews;
        this.colorPalette = colorPalette;
    }

    static boolean isValidMusicGain(float gain) {
        return !Float.isNaN(gain) && !Float.isInfinite(gain) && gain >= 0f && gain <= 1f;
    }

    static boolean isValidColorPalette(int palette) {
        return palette >= 0 && palette <= 3;
    }

    static boolean isValidOutputSize(int width, int height) {
        return width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0;
    }

    public List<Uri> getMediaUris() { return mediaUris; }
    public Uri getNarrationUri() { return narrationUri; }
    public Uri getMusicUri() { return musicUri; }
    public String getHeadline() { return headline; }
    public String getLowerThird() { return lowerThird; }
    public String getTicker() { return ticker; }
    public File getOutputFile() { return outputFile; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getImageDurationSeconds() { return imageDurationSeconds; }
    public float getMusicGain() { return musicGain; }
    public boolean isBreakingNews() { return breakingNews; }
    public int getColorPalette() { return colorPalette; }

    public VideoExportRequest withOutputFile(File replacementOutputFile) {
        return new VideoExportRequest(mediaUris, narrationUri, musicUri, headline, lowerThird,
                ticker, replacementOutputFile, width, height, imageDurationSeconds,
                musicGain, breakingNews, colorPalette);
    }
}
