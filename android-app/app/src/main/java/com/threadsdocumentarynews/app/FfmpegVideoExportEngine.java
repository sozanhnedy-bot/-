package com.threadsdocumentarynews.app;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-device FFmpeg-only export backend. Media is read through Android SAF and all
 * intermediate files stay in the app cache.
 */
public final class FfmpegVideoExportEngine implements VideoExportEngine {
    private static final int FPS = 30;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    // SAF URLs are reused by FFprobe and multiple FFmpeg commands during one export.
    private final List<String> registeredSafUrls = Collections.synchronizedList(new ArrayList<>());
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
        registeredSafUrls.clear();
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

            File overlayDirectory = new File(work, "news-overlays");
            renderOverlayFrames(overlayDirectory, request);
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
            args.add("-framerate"); args.add("10");
            args.add("-stream_loop"); args.add("-1");
            args.add("-i"); args.add(new File(overlayDirectory, "overlay-%03d.png").getAbsolutePath());

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
                    .append(" -c:v libx264 -preset ultrafast -b:v 6M -r ").append(FPS)
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
            synchronized (registeredSafUrls) {
                for (String safUrl : registeredSafUrls) {
                    try { FFmpegKitConfig.unregisterSafProtocolUrl(safUrl); } catch (Exception ignored) { }
                }
                registeredSafUrls.clear();
            }
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
                .append(" -an -c:v libx264 -preset ultrafast -b:v 6M -r ").append(FPS)
                .append(" -t ").append(seconds(input.durationMs))
                .append(" -movflags +faststart ").append(q(output.getAbsolutePath()));
        return command.toString();
    }

    private String readablePath(Uri uri) {
        if (uri == null) throw new IllegalArgumentException("Media URI is missing.");
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String path = FFmpegKitConfig.getSafParameterForRead(context, uri, true);
            if (path == null || path.trim().isEmpty()) throw new IllegalStateException("Android could not open the selected media URI.");
            registeredSafUrls.add(path);
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

    private void renderOverlayFrames(File directory, VideoExportRequest request) throws Exception {
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot create overlay frame directory.");
        final int frameCount = 20;
        for (int frame = 0; frame < frameCount; frame++) {
            checkCancelled();
            File output = new File(directory, String.format(Locale.US, "overlay-%03d.png", frame));
            renderOverlayFrame(output, request, frame / 10f);
        }
    }

    private void renderOverlayFrame(File file, VideoExportRequest request, float timeSeconds) throws Exception {
        int width = request.getWidth(), height = request.getHeight();
        float shortSide = Math.min(width, height);
        OverlayPalette palette = OverlayPalette.from(request.getColorPalette());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.drawColor(palette.background);
            drawPulseCircles(canvas, timeSeconds, width, height, shortSide);

            RectF card = new RectF(width * 0.075f, height * 0.105f, width * 0.925f, height * 0.89f);
            shape.setColor(Color.argb(58, 0, 0, 0));
            canvas.drawRoundRect(new RectF(card.left, card.top + shortSide * 0.018f, card.right, card.bottom + shortSide * 0.018f), shortSide * 0.045f, shortSide * 0.045f, shape);
            shape.setColor(palette.card);
            canvas.drawRoundRect(card, shortSide * 0.045f, shortSide * 0.045f, shape);

            RectF brand = new RectF(width * 0.15f, height * 0.14f, width * 0.85f, height * 0.198f);
            shape.setColor(palette.softPanel);
            canvas.drawRoundRect(brand, shortSide * 0.018f, shortSide * 0.018f, shape);
            drawText(canvas, "WORLD NEWS  •  أخبار العالم", brand.left, brand.top + shortSide * 0.009f,
                    brand.width(), shortSide * 0.028f, 1, palette.ink);

            RectF photo = new RectF(width * 0.145f, height * 0.218f, width * 0.855f, height * 0.493f);
            Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawRoundRect(photo, shortSide * 0.027f, shortSide * 0.027f, clear);
            shape.setColor(palette.accent);
            canvas.drawRoundRect(new RectF(photo.left, photo.top, photo.right, photo.top + shortSide * 0.010f), shortSide * 0.005f, shortSide * 0.005f, shape);

            RectF badge = new RectF(width * 0.395f, height * 0.468f, width * 0.605f, height * 0.525f);
            shape.setColor(Color.argb(38, 0, 0, 0));
            canvas.drawRoundRect(new RectF(badge.left, badge.top + shortSide * 0.006f, badge.right, badge.bottom + shortSide * 0.006f), shortSide * 0.03f, shortSide * 0.03f, shape);
            shape.setColor(palette.card);
            canvas.drawRoundRect(badge, shortSide * 0.03f, shortSide * 0.03f, shape);
            drawText(canvas, request.isBreakingNews() ? "عاجل" : "الآن", badge.left, badge.top + shortSide * 0.010f,
                    badge.width(), shortSide * 0.033f, 1, palette.ink);

            drawText(canvas, request.getHeadline().trim().isEmpty() ? "أخبار العالم" : request.getHeadline(),
                    width * 0.13f, height * 0.565f, width * 0.74f, shortSide * 0.052f, 3, palette.ink);
            if (!request.getLowerThird().trim().isEmpty()) {
                drawText(canvas, request.getLowerThird(), width * 0.15f, height * 0.755f,
                        width * 0.70f, shortSide * 0.029f, 2, palette.ink);
            }

            float footerTop = height * 0.848f;
            Path cardClip = new Path();
            cardClip.addRoundRect(card, shortSide * 0.045f, shortSide * 0.045f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(cardClip);
            shape.setColor(palette.footer);
            canvas.drawRect(card.left, footerTop, card.right, card.bottom, shape);
            shape.setColor(palette.accent);
            canvas.drawRect(card.left, footerTop, card.right, footerTop + shortSide * 0.007f, shape);

            text.setColor(palette.card);
            text.setTextSize(shortSide * 0.026f);
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            String ticker = request.getTicker().trim().isEmpty() ? "آخر التطورات" : request.getTicker().trim();
            String moving = "  " + ticker + "   •   WORLD NEWS   •   " + ticker + "  ";
            float textWidth = text.measureText(moving);
            float offset = VideoTransitionMath.tickerOffset((long) (timeSeconds * 1_000_000L), textWidth, shortSide * 0.09f);
            float baseline = footerTop + (card.bottom - footerTop) * 0.68f;
            for (float x = card.right - offset; x > card.left - textWidth; x -= textWidth) canvas.drawText(moving, x, baseline, text);
            canvas.restore();

            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
            }
        } finally {
            bitmap.recycle();
        }
    }

    private void drawPulseCircles(Canvas canvas, float timeSeconds, int width, int height, float shortSide) {
        Paint pulse = new Paint(Paint.ANTI_ALIAS_FLAG);
        pulse.setStyle(Paint.Style.STROKE);
        pulse.setStrokeWidth(Math.max(2f, shortSide * 0.003f));
        float[] centersX = {width * 0.78f, width * 0.20f, width * 0.62f};
        float[] centersY = {height * 0.27f, height * 0.58f, height * 0.53f};
        float[] phases = {0f, 0.8f, 1.5f};
        for (int i = 0; i < centersX.length; i++) {
            float phase = (timeSeconds + phases[i]) % 2.6f;
            float radius = shortSide * (0.035f + 0.28f * phase / 2.6f);
            pulse.setColor(Color.argb((int) (55f * (1f - phase / 2.6f)), 255, 150, 150));
            canvas.drawCircle(centersX[i], centersY[i], radius, pulse);
        }
    }

    private static final class OverlayPalette {
        final int background, card, ink, accent, footer, softPanel;
        private OverlayPalette(int background, int card, int ink, int accent, int footer, int softPanel) {
            this.background = background; this.card = card; this.ink = ink;
            this.accent = accent; this.footer = footer; this.softPanel = softPanel;
        }
        static OverlayPalette from(int index) {
            switch (index) {
                case 1: return new OverlayPalette(Color.rgb(17, 43, 78), Color.WHITE, Color.rgb(16, 37, 67), Color.rgb(220, 28, 40), Color.rgb(16, 37, 67), Color.rgb(239, 243, 248));
                case 2: return new OverlayPalette(Color.rgb(25, 26, 31), Color.rgb(250, 249, 246), Color.rgb(28, 31, 39), Color.rgb(230, 167, 45), Color.rgb(28, 31, 39), Color.rgb(239, 237, 232));
                case 3: return new OverlayPalette(Color.rgb(7, 105, 84), Color.WHITE, Color.rgb(16, 55, 55), Color.rgb(244, 188, 54), Color.rgb(16, 55, 55), Color.rgb(232, 244, 238));
                default: return new OverlayPalette(Color.rgb(220, 16, 27), Color.WHITE, Color.rgb(20, 35, 67), Color.rgb(220, 16, 27), Color.rgb(20, 35, 67), Color.rgb(245, 246, 249));
            }
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
