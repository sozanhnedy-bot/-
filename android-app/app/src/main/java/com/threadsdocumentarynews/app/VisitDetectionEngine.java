package com.threadsdocumentarynews.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Explainable, offline-only risk scorer for synthetic or lawfully collected
 * session telemetry. It does not connect to YouTube or create views.
 */
public final class VisitDetectionEngine {
    public static final int SUSPICIOUS_THRESHOLD = 50;

    private VisitDetectionEngine() {}

    /** Features for one test session. Do not put raw IPs or personal identifiers here. */
    public static final class Session {
        public final double watchDurationSeconds;
        public final double secondsSincePreviousView;
        public final int sessionsWithSamePseudonymousFingerprint;
        public final double repeatedDurationRatio;
        public final boolean automationSignal;

        public Session(double watchDurationSeconds,
                       double secondsSincePreviousView,
                       int sessionsWithSamePseudonymousFingerprint,
                       double repeatedDurationRatio,
                       boolean automationSignal) {
            this.watchDurationSeconds = Math.max(0, watchDurationSeconds);
            this.secondsSincePreviousView = Math.max(0, secondsSincePreviousView);
            this.sessionsWithSamePseudonymousFingerprint =
                    Math.max(1, sessionsWithSamePseudonymousFingerprint);
            this.repeatedDurationRatio = clamp(repeatedDurationRatio, 0, 1);
            this.automationSignal = automationSignal;
        }
    }

    public static final class Result {
        public final int riskScore;
        public final boolean suspicious;
        public final List<String> signals;

        private Result(int riskScore, boolean suspicious, List<String> signals) {
            this.riskScore = riskScore;
            this.suspicious = suspicious;
            this.signals = Collections.unmodifiableList(signals);
        }
    }

    public static final class BatchReport {
        public int total;
        public int suspicious;
        public int normal;
        public double averageRiskScore;
        public final List<Result> results = new ArrayList<>();
    }

    public static Result analyze(Session session) {
        if (session == null) throw new IllegalArgumentException("session must not be null");
        int score = 0;
        List<String> signals = new ArrayList<>();

        if (session.secondsSincePreviousView <= 1.5) {
            score += 30;
            signals.add("BURST_INTERVAL");
        }
        if (session.watchDurationSeconds <= 5.0) {
            score += 20;
            signals.add("VERY_SHORT_WATCH");
        }
        if (session.sessionsWithSamePseudonymousFingerprint >= 5) {
            score += 25;
            signals.add("REPEATED_FINGERPRINT");
        }
        if (session.repeatedDurationRatio >= 0.80) {
            score += 15;
            signals.add("REPEATED_DURATION_PATTERN");
        }
        if (session.automationSignal) {
            score += 40;
            signals.add("AUTOMATION_INDICATOR");
        }

        score = Math.min(100, score);
        return new Result(score, score >= SUSPICIOUS_THRESHOLD, signals);
    }

    public static BatchReport analyzeBatch(List<Session> sessions) {
        if (sessions == null) throw new IllegalArgumentException("sessions must not be null");
        BatchReport report = new BatchReport();
        long scoreSum = 0;
        for (Session session : sessions) {
            Result result = analyze(session);
            report.results.add(result);
            report.total++;
            scoreSum += result.riskScore;
            if (result.suspicious) report.suspicious++;
            else report.normal++;
        }
        report.averageRiskScore = report.total == 0 ? 0 : (double) scoreSum / report.total;
        return report;
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
