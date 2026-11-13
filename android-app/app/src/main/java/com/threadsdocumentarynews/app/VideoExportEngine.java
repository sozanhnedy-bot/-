package com.threadsdocumentarynews.app;

import java.io.File;

/**
 * Common contract for local video exporters.
 *
 * Implementations must invoke callbacks on the main thread. Calling cancel()
 * should be safe before, during, or after an export.
 */
public interface VideoExportEngine {
    void export(VideoExportRequest request, Listener listener);
    void cancel();

    interface Listener {
        void onProgress(int percent);
        void onCompleted(File outputFile);
        void onError(Exception error);
    }
}
