package com.threadsdocumentarynews.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Stores the latest sanitized media error for the in-app diagnostics screen. */
public final class DiagnosticStore {
    private static final String PREFS = "world-news-diagnostics";
    private static final String KEY_REPORT = "last_report";
    private static final String KEY_CATEGORY = "last_category";
    private static final String KEY_TIME = "last_time";
    private static final int MAX_REPORT_LENGTH = 18000;

    private DiagnosticStore() { }

    public static void record(Context context, String category, Throwable error) {
        String details = VideoExportDiagnostics.format(error);
        String report = "نوع العملية: " + safe(category)
                + "\nنسخة التطبيق: " + BuildConfig.VERSION_NAME
                + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\n\n" + details;
        recordReport(context, category, report);
    }

    public static void recordReport(Context context, String category, String report) {
        if (context == null) return;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date());
        String sanitized = sanitizeForDiagnostics(report == null || report.trim().isEmpty()
                ? "لم تتوفر تفاصيل إضافية للخطأ." : report);
        if (sanitized.length() > MAX_REPORT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_REPORT_LENGTH) + "\n[تم اختصار التقرير]";
        }
        prefs(context).edit()
                .putString(KEY_CATEGORY, safe(category))
                .putString(KEY_TIME, time)
                .putString(KEY_REPORT, sanitized)
                .apply();
    }

    public static String getReport(Context context) {
        return prefs(context).getString(KEY_REPORT, "لا توجد أخطاء مسجلة حتى الآن.");
    }

    public static String getCategory(Context context) {
        return prefs(context).getString(KEY_CATEGORY, "لا يوجد");
    }

    public static String getTime(Context context) {
        return prefs(context).getString(KEY_TIME, "");
    }

    public static boolean hasReport(Context context) {
        return !prefs(context).getString(KEY_REPORT, "").trim().isEmpty();
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_REPORT).remove(KEY_CATEGORY).remove(KEY_TIME).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "غير محدد" : value.trim();
    }

    static String sanitizeForDiagnostics(String value) {
        return value
                .replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+", "$1[محذوف]")
                .replaceAll("(?i)((?:api[_ -]?key|debug[_ -]?token|authorization)\\s*[:=]\\s*)[A-Za-z0-9._~+/-]{12,}", "$1[محذوف]")
                .replaceAll("AIza[0-9A-Za-z_-]{30,}", "[مفتاح API محذوف]");
    }
}
