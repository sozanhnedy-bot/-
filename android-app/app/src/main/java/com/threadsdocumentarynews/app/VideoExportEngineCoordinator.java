package com.threadsdocumentarynews.app;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.CancellationException;

/**
 * Runs the native engine first and uses Media3 only as a fallback. Each backend
 * writes to an independent path; only a completed, non-empty result is promoted
 * to the user's final output path.
 */
public final class VideoExportEngineCoordinator implements VideoExportEngine {
    private static final int PRIMARY_PROGRESS_RANGE = 80;

    private final VideoExportEngine primary;
    private final VideoExportEngine fallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private VideoExportRequest activeRequest;
    private Listener activeListener;
    private File primaryOutput;
    private boolean running;
    private boolean terminal;
    private boolean fallbackStarted;
    private int lastProgress;
    private long generation;

    public VideoExportEngineCoordinator(VideoExportEngine primary, VideoExportEngine fallback) {
        if (primary == null) throw new IllegalArgumentException("A primary export engine is required.");
        if (fallback == null) throw new IllegalArgumentException("A fallback export engine is required.");
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override public void export(VideoExportRequest request, Listener listener) {
        mainHandler.post(() -> begin(request, listener));
    }

    private void begin(VideoExportRequest request, Listener listener) {
        if (request == null || listener == null) {
            if (listener != null) listener.onError(new IllegalArgumentException("An export request is required."));
            return;
        }
        if (running) {
            listener.onError(new IllegalStateException("An export is already running."));
            return;
        }

        running = true;
        terminal = false;
        fallbackStarted = false;
        lastProgress = 0;
        activeRequest = request;
        activeListener = listener;
        final long jobGeneration = ++generation;

        File target = request.getOutputFile();
        File parent = target == null ? null : target.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            fail(jobGeneration, new IllegalStateException("Could not create the output directory."));
            return;
        }
        primaryOutput = new File(parent,
                target.getName() + ".ffmpeg-part-" + jobGeneration + ".mp4");
        if (primaryOutput.exists() && !primaryOutput.delete()) {
            fail(jobGeneration, new IllegalStateException("Could not clear the FFmpeg temporary output."));
            return;
        }

        try {
            primary.export(request.withOutputFile(primaryOutput), new Listener() {
                @Override public void onProgress(int percent) {
                    postForJob(jobGeneration, () -> updateProgress(
                            mapProgress(percent, 0, PRIMARY_PROGRESS_RANGE)));
                }

                @Override public void onCompleted(File outputFile) {
                    postForJob(jobGeneration, () -> onPrimaryCompleted(jobGeneration, outputFile));
                }

                @Override public void onError(Exception error) {
                    postForJob(jobGeneration, () -> startFallback(jobGeneration, error));
                }
            });
        } catch (Exception error) {
            startFallback(jobGeneration, error);
        }
    }

    private void onPrimaryCompleted(long jobGeneration, File outputFile) {
        File candidate = outputFile == null ? primaryOutput : outputFile;
        File target = activeRequest.getOutputFile();
        if (isUsableOutput(candidate) && promote(candidate, target)) {
            complete(jobGeneration, target);
        } else {
            startFallback(jobGeneration,
                    new IllegalStateException("FFmpeg did not produce a valid, promotable MP4 file."));
        }
    }

    private void startFallback(long jobGeneration, Exception primaryError) {
        if (!isCurrent(jobGeneration) || fallbackStarted) return;
        fallbackStarted = true;
        deleteQuietly(primaryOutput);
        primaryOutput = null;
        final int fallbackBase = lastProgress;

        try {
            fallback.export(activeRequest, new Listener() {
                @Override public void onProgress(int percent) {
                    postForJob(jobGeneration, () -> updateProgress(
                            mapProgress(percent, fallbackBase, 100 - fallbackBase)));
                }

                @Override public void onCompleted(File outputFile) {
                    postForJob(jobGeneration, () -> {
                        File target = activeRequest.getOutputFile();
                        if (isUsableOutput(outputFile) && outputFile.equals(target)) {
                            complete(jobGeneration, target);
                        } else {
                            fail(jobGeneration, combineErrors(primaryError,
                                    new IllegalStateException("Media3 fallback did not produce a valid MP4 file.")));
                        }
                    });
                }

                @Override public void onError(Exception fallbackError) {
                    postForJob(jobGeneration, () -> fail(jobGeneration,
                            combineErrors(primaryError, fallbackError)));
                }
            });
        } catch (Exception fallbackError) {
            fail(jobGeneration, combineErrors(primaryError, fallbackError));
        }
    }

    @Override public void cancel() {
        mainHandler.post(() -> {
            if (!running || terminal) return;
            terminal = true;
            running = false;
            generation++;
            try { primary.cancel(); } catch (Exception ignored) { }
            try { fallback.cancel(); } catch (Exception ignored) { }
            deleteQuietly(primaryOutput);
            primaryOutput = null;
            Listener listener = activeListener;
            clearActive();
            if (listener != null) listener.onError(new CancellationException("Video export cancelled."));
        });
    }

    private void complete(long jobGeneration, File output) {
        if (!isCurrent(jobGeneration)) return;
        Listener listener = activeListener;
        terminal = true;
        running = false;
        deleteQuietly(primaryOutput);
        primaryOutput = null;
        clearActive();
        if (listener != null) {
            listener.onProgress(100);
            listener.onCompleted(output);
        }
    }

    private void fail(long jobGeneration, Exception error) {
        if (!isCurrent(jobGeneration)) return;
        Listener listener = activeListener;
        terminal = true;
        running = false;
        deleteQuietly(primaryOutput);
        primaryOutput = null;
        clearActive();
        if (listener != null) listener.onError(error == null
                ? new IllegalStateException("Video export failed.") : error);
    }

    private void updateProgress(int progress) {
        if (!running || terminal || activeListener == null) return;
        int bounded = Math.max(0, Math.min(99, progress));
        lastProgress = Math.max(lastProgress, bounded);
        activeListener.onProgress(lastProgress);
    }

    private void postForJob(long jobGeneration, Runnable callback) {
        mainHandler.post(() -> {
            if (isCurrent(jobGeneration)) callback.run();
        });
    }

    private boolean isCurrent(long jobGeneration) {
        return running && !terminal && generation == jobGeneration;
    }

    private void clearActive() {
        activeRequest = null;
        activeListener = null;
        fallbackStarted = false;
    }

    static boolean isUsableOutput(File file) {
        return file != null && file.isFile() && file.length() > 0L;
    }

    static boolean promote(File temporary, File target) {
        if (!isUsableOutput(temporary) || target == null || target.exists()) return false;
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.equals(temporary.getAbsoluteFile().getParentFile())) return false;
        return temporary.renameTo(target);
    }

    static int mapProgress(int rawProgress, int base, int span) {
        int bounded = Math.max(0, Math.min(100, rawProgress));
        int safeBase = Math.max(0, Math.min(100, base));
        int safeSpan = Math.max(0, Math.min(100 - safeBase, span));
        return safeBase + (bounded * safeSpan / 100);
    }

    private static Exception combineErrors(Exception primaryError, Exception fallbackError) {
        Exception result = fallbackError == null
                ? new IllegalStateException("Both export engines failed.") : fallbackError;
        if (primaryError != null && primaryError != result) {
            try { result.addSuppressed(primaryError); } catch (Exception ignored) { }
        }
        return result;
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try { file.delete(); } catch (Exception ignored) { }
        }
    }
}
