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

    public VideoExportRequest(List<Uri> mediaUris, Uri narrationUri, Uri musicUri,
            String headline, String lowerThird, String ticker, File outputFile,
            int width, int height, int imageDurationSeconds, float musicGain) {
        if (mediaUris == null || mediaUris.isEmpty()) {
            throw new IllegalArgumentException("At least one image or video is required.");
        }
        if (outputFile == null) throw new IllegalArgumentException("An output file is required.");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Output dimensions must be positive.");
        if (imageDurationSeconds <= 0) throw new IllegalArgumentException("Image duration must be positive.");
        if (musicGain < 0f || musicGain > 1f) throw new IllegalArgumentException("Music gain must be between 0 and 1.");

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
}
