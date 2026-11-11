package com.threadsdocumentarynews.app;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.GainProcessor;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Media3 implementation of the app's local video export contract. */
public final class Media3VideoExportEngine implements VideoExportEngine {
    public interface OverlayFactory {
        Effect create(List<Long> clipDurationsUs);
    }

    private final Context context;
    private final OverlayFactory overlayFactory;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ProgressHolder progressHolder = new ProgressHolder();
    private Transformer transformer;
    private Listener activeListener;
    private boolean exporting;

    public Media3VideoExportEngine(Context context, OverlayFactory overlayFactory) {
        this.context = context.getApplicationContext();
        this.overlayFactory = overlayFactory;
    }

    @Override public void export(VideoExportRequest request, Listener listener) {
        if (request == null || listener == null) return;
        if (exporting) {
            mainHandler.post(() -> listener.onError(
                    new IllegalStateException("An export is already running.")));
            return;
        }
        if (overlayFactory == null) {
            mainHandler.post(() -> listener.onError(
                    new IllegalStateException("An overlay factory is required.")));
            return;
        }
        activeListener = listener;
        exporting = true;
        try {
            ArrayList<EditedMediaItem> videoItems = new ArrayList<>();
            ArrayList<Long> durationsMs = new ArrayList<>();
            long totalDurationMs = 0L;

            for (Uri uri : request.getMediaUris()) {
                String mime = context.getContentResolver().getType(uri);
                long duration = mime != null && mime.startsWith("image/")
                        ? request.getImageDurationSeconds() * 1000L : getDurationMs(uri);
                if (duration <= 0L) {
                    throw new IllegalStateException("Could not read duration for media: " + uri);
                }
                durationsMs.add(duration);
                totalDurationMs += duration;

                MediaItem item;
                EditedMediaItem.Builder edited;
                if (mime != null && mime.startsWith("image/")) {
                    item = new MediaItem.Builder().setUri(uri)
                            .setImageDurationMs(request.getImageDurationSeconds() * 1000L).build();
                    edited = new EditedMediaItem.Builder(item).setFrameRate(30);
                } else {
                    item = MediaItem.fromUri(uri);
                    edited = new EditedMediaItem.Builder(item).setRemoveAudio(true);
                }
                ArrayList<androidx.media3.common.Effect> videoEffects = new ArrayList<>();
                videoEffects.add(Presentation.createForWidthAndHeight(
                        request.getWidth(), request.getHeight(),
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
                edited.setEffects(new Effects(Collections.emptyList(), videoEffects));
                videoItems.add(edited.build());
            }

            ArrayList<EditedMediaItemSequence> sequences = new ArrayList<>();
            sequences.add(EditedMediaItemSequence.withVideoFrom(videoItems));

            if (request.getNarrationUri() != null) {
                MediaItem narration = new MediaItem.Builder().setUri(request.getNarrationUri())
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setEndPositionMs(totalDurationMs).build()).build();
                EditedMediaItem narrationItem = new EditedMediaItem.Builder(narration).build();
                sequences.add(EditedMediaItemSequence.withAudioFrom(
                        Collections.singletonList(narrationItem)).buildUpon()
                        .setIsLooping(false).build());
            }

            if (request.getMusicUri() != null) {
                long sourceDurationMs = getDurationMs(request.getMusicUri());
                if (sourceDurationMs <= 0L) {
                    throw new IllegalStateException("Could not read background audio duration.");
                }
                final long musicDurationUs = Math.min(sourceDurationMs, totalDurationMs) * 1000L;
                MediaItem music = new MediaItem.Builder().setUri(request.getMusicUri())
                        .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                                .setEndPositionMs(musicDurationUs / 1000L).build()).build();
                final float baseGain = request.getMusicGain();
                GainProcessor gain = new GainProcessor(new GainProcessor.GainProvider() {
                    @Override public float getGainFactorAtSamplePosition(long samplePosition, int sampleRate) {
                        if (sampleRate <= 0) return baseGain;
                        long elapsedUs = samplePosition * 1_000_000L / sampleRate;
                        return VideoTransitionMath.musicGainAt(elapsedUs, musicDurationUs, baseGain);
                    }
                    @Override public long isUnityUntil(long samplePosition, int sampleRate) {
                        return Long.MIN_VALUE;
                    }
                });
                EditedMediaItem musicItem = new EditedMediaItem.Builder(music)
                        .setEffects(new Effects(Collections.singletonList(gain), Collections.emptyList()))
                        .build();
                sequences.add(EditedMediaItemSequence.withAudioFrom(
                        Collections.singletonList(musicItem)).buildUpon()
                        .setIsLooping(false).build());
            }

            ArrayList<Long> durationsUs = new ArrayList<>();
            for (Long duration : durationsMs) durationsUs.add(duration * 1000L);
            Effect overlay = overlayFactory.create(Collections.unmodifiableList(durationsUs));
            Composition composition = new Composition.Builder(sequences)
                    .setEffects(new Effects(Collections.emptyList(),
                            Collections.singletonList(new OverlayEffect(Collections.singletonList(overlay)))))
                    .build();

            File output = request.getOutputFile();
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Could not create output directory.");
            }
            transformer = new Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition finished, ExportResult result) {
                            finish();
                            if (output.isFile() && output.length() > 0L) listener.onCompleted(output);
                            else listener.onError(new IllegalStateException(
                                    "Export finished but the output file is missing."));
                        }
                        @Override public void onError(Composition failed, ExportResult result,
                                ExportException error) {
                            finish();
                            listener.onError(error);
                        }
                    }).build();
            transformer.start(composition, output.getAbsolutePath());
            mainHandler.post(progressPoller);
        } catch (Exception error) {
            finish();
            mainHandler.post(() -> listener.onError(error));
        }
    }

    private final Runnable progressPoller = new Runnable() {
        @Override public void run() {
            if (!exporting || transformer == null || activeListener == null) return;
            int state = transformer.getProgress(progressHolder);
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                activeListener.onProgress(progressHolder.progress);
            }
            if (exporting) mainHandler.postDelayed(this, 500L);
        }
    };

    @Override public void cancel() {
        if (exporting && transformer != null) {
            try { transformer.cancel(); } catch (Exception ignored) { }
        }
    }

    private void finish() {
        exporting = false;
        mainHandler.removeCallbacks(progressPoller);
        transformer = null;
        activeListener = null;
    }

    private long getDurationMs(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0L : Math.max(0L, Long.parseLong(value));
        } catch (Exception ignored) {
            return 0L;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }
}
