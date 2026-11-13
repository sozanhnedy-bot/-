package com.threadsdocumentarynews.app;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Formats complete export error chains for the in-app diagnostic dialog. */
final class VideoExportDiagnostics {
    private static final int MAX_LENGTH = 12000;
    private static final String TRUNCATION_MARKER = "\n[تم اختصار سجل التشخيص]";

    private VideoExportDiagnostics() { }

    static String format(Throwable error) {
        if (error == null) return "تعذر الحصول على تفاصيل إضافية.";
        StringBuilder report = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        appendThrowable(report, error, "الخطأ", visited, 0);
        return report.length() == 0 ? "تعذر الحصول على تفاصيل إضافية." : report.toString().trim();
    }

    private static void appendThrowable(StringBuilder report, Throwable error, String label,
            Set<Throwable> visited, int depth) {
        if (error == null || depth > 16 || report.length() >= MAX_LENGTH || !visited.add(error)) return;
        String message = error.getMessage();
        String line = label + ": " + error.getClass().getSimpleName();
        if (message != null && !message.trim().isEmpty()) line += " — " + redact(message);
        appendLimited(report, line + "\n");
        if (report.length() >= MAX_LENGTH) return;

        appendThrowable(report, error.getCause(), "السبب", visited, depth + 1);
        for (Throwable suppressed : error.getSuppressed()) {
            appendThrowable(report, suppressed, "خطأ إضافي", visited, depth + 1);
            if (report.length() >= MAX_LENGTH) return;
        }
    }

    private static String redact(String message) {
        return message
                .replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+", "$1[محذوف]")
                .replaceAll("(?i)((?:api[_ -]?key|token|authorization)\\s*[:=]\\s*)[A-Za-z0-9._~+/-]{12,}", "$1[محذوف]");
    }

    private static void appendLimited(StringBuilder report, String value) {
        int remaining = MAX_LENGTH - report.length();
        if (remaining <= 0) return;
        if (value.length() <= remaining) {
            report.append(value);
            return;
        }
        int markerLength = Math.min(TRUNCATION_MARKER.length(), remaining);
        int textLength = Math.max(0, remaining - markerLength);
        report.append(value, 0, Math.min(textLength, value.length()));
        report.append(TRUNCATION_MARKER, 0, markerLength);
    }
}
