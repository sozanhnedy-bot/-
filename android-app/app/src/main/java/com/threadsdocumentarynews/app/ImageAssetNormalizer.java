package com.threadsdocumentarynews.app;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Movie;
import android.media.ExifInterface;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Decodes a user-selected image into a bounded, orientation-correct bitmap. */
public final class ImageAssetNormalizer {
    public static final long MAX_COMPRESSED_BYTES = 20L * 1024L * 1024L;
    public static final long MAX_DECODE_PIXELS = 24_000_000L;
    private static final int MAX_EDGE = 4096;

    private ImageAssetNormalizer() { }

    public static Bitmap decode(ContentResolver resolver, Uri uri) throws IOException {
        if (resolver == null || uri == null) throw new IOException("لم يتم تحديد صورة.");
        String mime = resolver.getType(uri);
        if (mime != null && mime.equalsIgnoreCase("image/gif")) return decodeGifCover(resolver, uri);
        if (mime != null && (mime.equalsIgnoreCase("image/svg+xml") || mime.equalsIgnoreCase("image/heic") || mime.equalsIgnoreCase("image/heif"))) {
            throw new IOException("هذه الصيغة غير مدعومة بأمان. اختر صورة JPEG أو PNG أو WebP.");
        }
        long size = -1;
        try (android.database.Cursor cursor = resolver.query(uri, new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) size = cursor.getLong(0);
        } catch (RuntimeException ignored) { }
        if (size > MAX_COMPRESSED_BYTES) throw new IOException("حجم الصورة أكبر من الحد المسموح (20 ميغابايت).");

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("تعذر فتح ملف الصورة.");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            if (mime != null && mime.equalsIgnoreCase("image/gif")) throw new IOException("ملفات GIF المتحركة غير مدعومة؛ اختر صورة ثابتة.");
            throw new IOException("تعذر فك ترميز الصورة. اختر JPEG أو PNG أو WebP صالحًا.");
        }
        long pixels = (long) bounds.outWidth * bounds.outHeight;
        if (pixels > MAX_DECODE_PIXELS * 16L) throw new IOException("أبعاد الصورة ضخمة جدًا ولا يمكن معالجتها بأمان.");
        int sample = 1;
        while ((bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE || pixels / ((long) sample * sample) > MAX_DECODE_PIXELS) && sample < 128) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("تعذر فتح ملف الصورة.");
            decoded = BitmapFactory.decodeStream(input, null, options);
        } catch (OutOfMemoryError error) {
            throw new IOException("تعذر توفير ذاكرة كافية للصورة؛ اختر صورة أصغر.");
        }
        if (decoded == null) throw new IOException("صيغة الصورة غير مدعومة أو الملف تالف. استخدم JPEG أو PNG أو WebP.");

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input != null) orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) { /* EXIF is optional; keep decoded pixels. */ }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1, 1); break;
            default: break;
        }
        if (matrix.isIdentity()) return decoded;
        try {
            Bitmap oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
            if (oriented != decoded) decoded.recycle();
            return oriented;
        } catch (RuntimeException | OutOfMemoryError error) {
            decoded.recycle();
            throw new IOException("تعذر تدوير الصورة بأمان.");
        }
    }

    private static Bitmap decodeGifCover(ContentResolver resolver, Uri uri) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("تعذر فتح ملف GIF.");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (data.size() + count > MAX_COMPRESSED_BYTES) {
                    throw new IOException("حجم GIF أكبر من الحد المسموح (20 ميغابايت).");
                }
                data.write(buffer, 0, count);
            }
        }
        byte[] bytes = data.toByteArray();
        Movie movie = Movie.decodeStream(new ByteArrayInputStream(bytes));
        if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
            throw new IOException("تعذر فك GIF. اختر ملفًا صالحًا أو احفظ إطارًا منه كصورة ثابتة.");
        }
        long pixels = (long) movie.width() * movie.height();
        if (pixels > MAX_DECODE_PIXELS) throw new IOException("أبعاد GIF ضخمة جدًا ولا يمكن معالجتها بأمان.");
        float scale = Math.min(1f, Math.min(MAX_EDGE / (float) movie.width(), MAX_EDGE / (float) movie.height()));
        int width = Math.max(1, Math.round(movie.width() * scale));
        int height = Math.max(1, Math.round(movie.height() * scale));
        Bitmap cover;
        try {
            cover = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(cover);
            canvas.scale(scale, scale);
            movie.setTime(0);
            movie.draw(canvas, 0, 0);
            return cover;
        } catch (RuntimeException | OutOfMemoryError error) {
            throw new IOException("تعذر إنشاء صورة غلاف من GIF بأمان.");
        }
    }

    public static String extensionFor(ContentResolver resolver, Uri uri) {
        String mime = resolver.getType(uri);
        if (mime != null) {
            if ("image/png".equalsIgnoreCase(mime)) return "png";
            if ("image/jpeg".equalsIgnoreCase(mime)) return "jpg";
            if ("image/webp".equalsIgnoreCase(mime)) return "webp";
        }
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        return extension == null ? "" : extension.toLowerCase(java.util.Locale.ROOT);
    }
}
