package com.threadsdocumentarynews.app;

import java.util.Random;

/**
 * Offline-only test-data generator. Every generated session is passed through
 * VisitDetectionEngine; this class never opens the supplied YouTube URL.
 */
public final class SyntheticTrafficEngine {
    private SyntheticTrafficEngine() {}

    public static final class BatchResult {
        public int total, normal, automated;
        public int truePositive, falsePositive, trueNegative, falseNegative;
        public double averageDurationSeconds, averageGapSeconds;

        public double accuracy() {
            return total == 0 ? 0 : (double)(truePositive + trueNegative) / total;
        }
        public double precision() {
            int denominator = truePositive + falsePositive;
            return denominator == 0 ? 0 : (double)truePositive / denominator;
        }
        public double recall() {
            int denominator = truePositive + falseNegative;
            return denominator == 0 ? 0 : (double)truePositive / denominator;
        }
        public void add(BatchResult other) {
            int oldTotal = total;
            int newTotal = oldTotal + other.total;
            if (newTotal > 0) {
                averageDurationSeconds = (averageDurationSeconds * oldTotal
                        + other.averageDurationSeconds * other.total) / newTotal;
                averageGapSeconds = (averageGapSeconds * oldTotal
                        + other.averageGapSeconds * other.total) / newTotal;
            }
            total = newTotal;
            normal += other.normal;
            automated += other.automated;
            truePositive += other.truePositive;
            falsePositive += other.falsePositive;
            trueNegative += other.trueNegative;
            falseNegative += other.falseNegative;
        }
    }

    public static BatchResult generate(int count, Random random) {
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        if (random == null) throw new IllegalArgumentException("random must not be null");

        BatchResult out = new BatchResult();
        double durationSum = 0;
        double gapSum = 0;
        for (int i = 0; i < count; i++) {
            // Ground truth: 55% ordinary, 25% scripted, 20% burst/replay.
            int profile = random.nextInt(100);
            boolean automated = profile >= 55;
            double duration;
            double gap;
            int repeatedFingerprintCount;
            double repeatedDurationRatio;
            boolean automationSignal;

            if (profile < 55) {
                duration = 18 + random.nextDouble() * 150;
                gap = 8 + random.nextDouble() * 90;
                repeatedFingerprintCount = random.nextInt(4) + 1;
                repeatedDurationRatio = random.nextDouble() * 0.55;
                automationSignal = random.nextInt(100) < 3;
            } else if (profile < 80) {
                duration = 3 + random.nextDouble() * 5;
                gap = 0.4 + random.nextDouble() * 1.5;
                repeatedFingerprintCount = 5 + random.nextInt(15);
                repeatedDurationRatio = 0.80 + random.nextDouble() * 0.20;
                automationSignal = random.nextInt(100) < 75;
            } else {
                duration = 1 + random.nextDouble() * 2;
                gap = random.nextDouble() * 0.25;
                repeatedFingerprintCount = 8 + random.nextInt(25);
                repeatedDurationRatio = 0.90 + random.nextDouble() * 0.10;
                automationSignal = random.nextInt(100) < 90;
            }

            VisitDetectionEngine.Session session = new VisitDetectionEngine.Session(
                    duration, gap, repeatedFingerprintCount,
                    repeatedDurationRatio, automationSignal);
            VisitDetectionEngine.Result detection = VisitDetectionEngine.analyze(session);

            out.total++;
            if (automated) {
                out.automated++;
                if (detection.suspicious) out.truePositive++;
                else out.falseNegative++;
            } else {
                out.normal++;
                if (detection.suspicious) out.falsePositive++;
                else out.trueNegative++;
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
