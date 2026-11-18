package com.threadsdocumentarynews.app;

import java.util.Random;

/**
 * Generates offline-only synthetic traffic profiles for testing a detector.
 * No sockets, HTTP clients, WebViews, or platform traffic are used here.
 */
public final class SyntheticTrafficEngine {
    private SyntheticTrafficEngine() {}

    public static final class BatchResult {
        public int total, normal, automated, truePositive, falsePositive, trueNegative, falseNegative;
        public double averageDurationSeconds, averageGapSeconds;
        public double precision() { return ratio(truePositive, truePositive + falsePositive); }
        public double recall() { return ratio(truePositive, truePositive + falseNegative); }
        public double accuracy() { return ratio(truePositive + trueNegative, total); }
        private static double ratio(int n, int d) { return d == 0 ? 0.0 : (double)n / d; }
        public void add(BatchResult other) {
            int previousTotal = total;
            total += other.total; normal += other.normal; automated += other.automated;
            truePositive += other.truePositive; falsePositive += other.falsePositive;
            trueNegative += other.trueNegative; falseNegative += other.falseNegative;
            averageDurationSeconds = (averageDurationSeconds * previousTotal
                + other.averageDurationSeconds * other.total) / total;
            averageGapSeconds = (averageGapSeconds * previousTotal
                + other.averageGapSeconds * other.total) / total;
        }
        public void finishAverages() {
            if (total > 0) { averageDurationSeconds /= total; averageGapSeconds /= total; }
        }
    }

    public static BatchResult generate(int count, Random random) {
        BatchResult out = new BatchResult();
        double durationSum = 0, gapSum = 0;
        for (int i = 0; i < count; i++) {
            // 55% ordinary, 25% scripted automation, 20% burst/replay behavior.
            int profile = random.nextInt(100);
            boolean automated = profile >= 55;
            double duration = automated
                ? (profile < 80 ? 3 + random.nextDouble() * 5 : 1 + random.nextDouble() * 2)
                : 18 + random.nextDouble() * 150;
            double gap = automated
                ? (profile < 80 ? 0.4 + random.nextDouble() * 1.5 : random.nextDouble() * 0.25)
                : 8 + random.nextDouble() * 90;
            boolean repeatedFingerprint = automated
                ? random.nextInt(100) < 82 : random.nextInt(100) < 8;
            boolean synchronizedStart = automated
                ? random.nextInt(100) < 75 : random.nextInt(100) < 5;

            int score = 0;
            if (duration < 8) score += 2;
            if (gap < 2) score += 2;
            if (repeatedFingerprint) score += 2;
            if (synchronizedStart) score += 2;
            boolean flagged = score >= 4;

            out.total++;
            if (automated) {
                out.automated++;
                if (flagged) out.truePositive++; else out.falseNegative++;
            } else {
                out.normal++;
                if (flagged) out.falsePositive++; else out.trueNegative++;
            }
            durationSum += duration;
            gapSum += gap;
        }
        if (count > 0) {
            out.averageDurationSeconds = durationSum / count;
            out.averageGapSeconds = gapSum / count;
        }
        return out;
    }
}
