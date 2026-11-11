package com.threadsdocumentarynews.app;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-device FFmpeg export backend. Media is read through Android SAF and all
 * intermediate files stay in the app cache. Media3 remains a coordinator fallback.
 */
public final class FfmpegVideoExportEngine implements VideoExportEngine {
    private static final int FPS = 30;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean running;
    private volatile Listener listener;

    public FfmpegVideoExportEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public synchronized void export(VideoExportRequest request, Listener callback) {
        if (request == null || callback == null) return;
        if (running) {
            main.post(() -> callback.onError(new IllegalStateException("An FFmpeg export is already running.")));
            return;
        }
        running = true;
        cancelled.set(false);
        listener = callback;
        worker.execute(() -> runExport(request, callback));
    }

    private void runExport(VideoExportRequest request, Listener callback) {
        File work = null;
        File stagedOutput = null;
        try {
            File cache = new File(context.getCacheDir(), "ffmpeg-video-export");
            if (!cache.exists() && !cache.mkdirs()) throw new IllegalStateException("Cannot create FFmpeg cache directory.");
            work = new File(cache, "job-" + System.nanoTime());
            if (!work.mkdirs()) throw new IllegalStateException("Cannot create FFmpeg job directory.");
            File target = request.getOutputFile().getAbsoluteFile();
            File parent = target.getParentFile();
            if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                throw new IllegalStateException("Cannot create output directory.");
            }
            stagedOutput = new File(parent, target.getName() + ".native-part.mp4");
            delete(stagedOutput);

            List<MediaInput> inputs = new ArrayList<>();
            long totalMs = 0L;
            int index = 0;
            for (Uri uri : request.getMediaUris()) {
                checkCancelled();
                String path = readablePath(uri);
                boolean image = isImage(uri);
                long durationMs = image ? request.getImageDurationSeconds() * 1000L : probeDurationMs(path);
                if (durationMs <= 0L) throw new IllegalStateException("Could not determine media duration: " + uri);
                boolean hasAudio = !image && hasAudio(path);
                inputs.add(new MediaInput(path, image, durationMs, hasAudio, index++));
                totalMs += durationMs;
            }
            if (totalMs <= 0L) throw new IllegalStateException("The selected media has no duration.");

            List<File> videoSegments = new ArrayList<>();
            List<File> audioSegments = new ArrayList<>();
            long completedDuration = 0L;
            for (MediaInput input : inputs) {
                checkCancelled();
                File video = new File(work, "video-" + input.index + ".mp4");
                String videoCommand = segmentCommand(input, request, video);
                execute(videoCommand, "video segment " + (input.index + 1));
                videoSegments.add(video);

                File audio = new File(work, "audio-" + input.index + ".wav");
                if (input.hasAudio) {
                    execute("-y -i " + q(input.path) + " -map 0:a:0 -vn -t "
                            + seconds(input.durationMs) + " -af "
                            + q("aresample=48000, aformat=sample_fmts=s16:channel_layouts=stereo,apad,atrim=duration=" + seconds(input.durationMs))
                            + " -ac 2 -ar 48000 -c:a pcm_s16le " + q(audio.getAbsolutePath()),
                            "source audio " + (input.index + 1));
                } else {
                    execute("-y -f lavfi -i anullsrc=channel_layout=stereo:sample_rate=48000 -t "
                            + seconds(input.durationMs) + " -c:a pcm_s16le " + q(audio.getAbsolutePath()),
                            "silent audio " + (input.index + 1));
                }
                audioSegments.add(audio);
                completedDuration += input.durationMs;
                progress(callback, 5 + (int) (completedDuration * 55L / totalMs));
            }

            File concatList = new File(work, "video-list.txt");
            writeConcatList(concatList, videoSegments);
            File joinedVideo = new File(work, "joined-video.mp4");
            execute("-y -f concat -safe 0 -i " + q(concatList.getAbsolutePath())
                    + " -c copy -movflags +faststart " + q(joinedVideo.getAbsolutePath()), "join video segments");
            progress(callback, 65);

            File overlay = new File(work, "news-overlay.png");
            renderOverlay(overlay, request);
            File audioList = new File(work, "audio-list.txt");
            writeConcatList(audioList, audioSegments);
            File joinedAudio = new File(work, "joined-audio.wav");
            execute("-y -f concat -safe 0 -i " + q(audioList.getAbsolutePath())
                    + " -c:a pcm_s16le " + q(joinedAudio.getAbsolutePath()), "join source audio");

            List<String> args = new ArrayList<>();
            args.add("-y"); args.add("-i"); args.add(joinedVideo.getAbsolutePath());
            args.add("-i"); args.add(joinedAudio.getAbsolutePath());
            int nextInput = 2;
            int narrationIndex = -1;
            int musicIndex = -1;
            if (request.getNarrationUri() != null) {
                narrationIndex = nextInput++;
                args.add("-i"); args.add(readablePath(request.getNarrationUri()));
            }
            if (request.getMusicUri() != null) {
                musicIndex = nextInput++;
                args.add("-i"); args.add(readablePath(request.getMusicUri()));
            }
            int overlayIndex = nextInput;
            args.add("-loop"); args.add("1"); args.add("-i"); args.add(overlay.getAbsolutePath());

            StringBuilder graph = new StringBuilder();
            graph.append("[0:v][").append(overlayIndex)
                    .append(":v]overlay=0:0:shortest=1,format=yuv420p[vout];");
            List<String> audioLabels = new ArrayList<>();
            graph.append("[1:a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo,apad,atrim=duration=")
                    .append(seconds(totalMs)).append("[src]");
            audioLabels.add("[src]");
            if (narrationIndex >= 0) {
                graph.append(";[").append(narrationIndex)
                        .append(":a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo,apad,atrim=duration=")
                        .append(seconds(totalMs)).append("[narr]");
                audioLabels.add("[narr]");
            }
            if (musicIndex >= 0) {
                graph.append(";[").append(musicIndex).append(":a]aresample=48000,volume=")
                        .append(String.format(Locale.US, "%.3f", request.getMusicGain()))
                        .append(",afade=t=out:st=").append(seconds(Math.max(0L, totalMs - 2000L)))
                        .append(":d=").append(seconds(Math.min(2000L, totalMs)))
                        .append(",aformat=sample_fmts=fltp:channel_layouts=stereo,apad,atrim=duration=")
                        .append(seconds(totalMs)).append("[music]");
                audioLabels.add("[music]");
            }
            graph.append(';');
            for (String label : audioLabels) graph.append(label);
            graph.append("amix=inputs=").append(audioLabels.size())
                    .append(":duration=longest:dropout_transition=0,alimiter=limit=0.95,aresample=48000[aout]");

            StringBuilder command = new StringBuilder();
            for (String arg : args) command.append(q(arg)).append(' ');
            command.append("-filter_complex ").append(q(graph.toString()))
                    .append(" -map ").append(q("[vout]"))
                    .append(" -map ").append(q("[aout]"))
                    .append(" -c:v h264_mediacodec -b:v 6M -r ").append(FPS)
                    .append(" -pix_fmt yuv420p -c:a aac -b:a 192k -ar 48000 -ac 2")
                    .append(" -movflags +faststart -shortest ").append(q(stagedOutput.getAbsolutePath()));
            execute(command.toString(), "final MP4 mux");
            checkCancelled();
            if (!stagedOutput.isFile() || stagedOutput.length() < 1024L) {
                throw new IllegalStateException("FFmpeg completed without producing a valid MP4.");
            }
            if (target.exists() || !stagedOutput.renameTo(target)) {
                throw new IllegalStateException("Could not promote the completed MP4 to its output path.");
            }
            progress(callback, 100);
            finish(callback, target, null);
        } catch (Exception error) {
            delete(stagedOutput);
            if (cancelled.get()) finish(callback, null, new java.util.concurrent.CancellationException("Video export cancelled."));
            else finish(callback, null, error);
        } finally {
            deleteRecursively(work);
        }
    }

    private String segmentCommand(MediaInput input, VideoExportRequest request, File output) {
        StringBuilder command = new StringBuilder("-y ");
        if (input.image) command.append("-loop 1 -framerate ").append(FPS).append(" -t ")
                .append(seconds(input.durationMs)).append(" -i ").append(q(input.path)).append(' ');
        else command.append("-i ").append(q(input.path)).append(" -t ").append(seconds(input.durationMs)).append(' ');
        command.append("-vf ").append(q("scale=" + request.getWidth() + ":" + request.getHeight()
                + ":force_original_aspect_ratio=increase,crop=" + request.getWidth() + ":" + request.getHeight()
                + ",setsar=1,fps=" + FPS + ",format=yuv420p"))
                .append(" -an -c:v h264_mediacodec -b:v 6M -r ").append(FPS)
                .append(" -t ").append(seconds(input.durationMs))
                .append(" -movflags +faststart ").append(q(output.getAbsolutePath()));
        return command.toString();
    }

    private String readablePath(Uri uri) {
        if (uri == null) throw new IllegalArgumentException("Media URI is missing.");
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String path = FFmpegKitConfig.getSafParameterForRead(context, uri);
            if (path == null || path.trim().isEmpty()) throw new IllegalStateException("Android could not open the selected media URI.");
            return path;
        }
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) return uri.getPath();
        throw new IllegalArgumentException("Unsupported media URI scheme: " + uri.getScheme());
    }

    private boolean isImage(Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("image/")) return true;
        String value = (uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment()).toLowerCase(Locale.ROOT);
        return value.matches(".*\\.(jpg|jpeg|png|webp|bmp|gif)$");
    }

    private long probeDurationMs(String path) {
        FFprobeSession session = FFprobeKit.execute("-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 " + q(path));
        if (!ReturnCode.isSuccess(session.getReturnCode())) {
            throw new IllegalStateException("FFprobe could not read media duration: " + session.getOutput());
        }
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(session.getOutput() == null ? "" : session.getOutput());
        if (!matcher.find()) throw new IllegalStateException("FFprobe returned no media duration.");
        return Math.max(1L, (long) (Double.parseDouble(matcher.group(1)) * 1000.0));
    }

    private boolean hasAudio(String path) {
        FFprobeSession session = FFprobeKit.execute("-v error -select_streams a:0 -show_entries stream=index -of csv=p=0 " + q(path));
        return ReturnCode.isSuccess(session.getReturnCode())
                && session.getOutput() != null && !session.getOutput().trim().isEmpty();
    }

    private void execute(String command, String stage) {
        checkCancelled();
        FFmpegSession session = FFmpegKit.execute(command);
        checkCancelled();
        if (!ReturnCode.isSuccess(session.getReturnCode())) {
            String detail = session.getOutput();
            if (detail == null || detail.trim().isEmpty()) detail = session.getFailStackTrace();
            throw new IllegalStateException("FFmpeg failed during " + stage + ": " + (detail == null ? "unknown error" : detail));
        }
    }

    private void renderOverlay(File file, VideoExportRequest request) throws Exception {
        int width = request.getWidth(), height = request.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale = width / 720f;
        paint.setColor(Color.argb(220, 10, 13, 20));
        canvas.drawRect(0, 0, width, height * 0.16f, paint);
        paint.setColor(request.isBreakingNews() ? Color.rgb(195, 30, 35) : Color.rgb(22, 53, 82));
        canvas.drawRect(0, height * 0.16f, width, height * 0.165f, paint);
        drawText(canvas, "WORLD NEWS", width * 0.06f, height * 0.045f, width * 0.88f, 34f * scale, 1, Color.WHITE);
        if (!request.getHeadline().trim().isEmpty()) {
            paint.setColor(Color.argb(205, 5, 8, 14));
            canvas.drawRoundRect(new RectF(width * 0.05f, height * 0.70f, width * 0.95f, height * 0.84f),
                    14f * scale, 14f * scale, paint);
            drawText(canvas, request.getHeadline(), width * 0.08f, height * 0.715f, width * 0.84f,
                    30f * scale, 2, Color.WHITE);
        }
        if (!request.getLowerThird().trim().isEmpty()) {
            drawText(canvas, request.getLowerThird(), width * 0.08f, height * 0.845f, width * 0.84f,
                    19f * scale, 2, Color.rgb(255, 211, 115));
        }
        paint.setColor(request.isBreakingNews() ? Color.rgb(180, 20, 26) : Color.rgb(18, 42, 65));
        canvas.drawRect(0, height * 0.925f, width, height, paint);
        drawText(canvas, request.getTicker(), width * 0.04f, height * 0.94f, width * 0.92f,
                18f * scale, 1, Color.WHITE);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        } finally {
            bitmap.recycle();
        }
    }

    private void drawText(Canvas canvas, String value, float x, float y, float maxWidth,
            float textSize, int maxLines, int color) {
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(color);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        StaticLayout layout = StaticLayout.Builder.obtain(value, 0, value.length(), textPaint, Math.max(1, (int) maxWidth))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
    }

    private void writeConcatList(File list, List<File> files) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(list), StandardCharsets.UTF_8))) {
            for (File file : files) {
                String path = file.getAbsolutePath().replace("'", "'\\''");
                writer.write("file '" + path + "'");
                writer.newLine();
            }
        }
    }

    private void progress(Listener callback, int value) {
        main.post(() -> { if (listener == callback && !cancelled.get()) callback.onProgress(Math.max(0, Math.min(99, value))); });
    }

    private void finish(Listener callback, File output, Exception error) {
        synchronized (this) {
            if (listener != callback) return;
            listener = null;
            running = false;
        }
        main.post(() -> {
            if (error == null) callback.onCompleted(output);
            else callback.onError(error);
        });
    }

    @Override public void cancel() {
        cancelled.set(true);
        FFmpegKit.cancel();
    }

    private void checkCancelled() {
        if (cancelled.get()) throw new java.util.concurrent.CancellationException("Video export cancelled.");
    }

    private static String seconds(long millis) {
        return String.format(Locale.US, "%.3f", millis / 1000.0);
    }

    private static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void delete(File file) {
        if (file != null && file.exists()) file.delete();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        delete(file);
    }

    private static final class MediaInput {
        final String path;
        final boolean image;
        final long durationMs;
        final boolean hasAudio;
        final int index;
        MediaInput(String path, boolean image, long durationMs, boolean hasAudio, int index) {
            this.path = path; this.image = image; this.durationMs = durationMs;
            this.hasAudio = hasAudio; this.index = index;
        }
    }
}
