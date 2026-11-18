package com.threadsdocumentarynews.app;

import java.util.Random;

/** Offline-only synthetic session generator. No network traffic is sent. */
public final class SyntheticTrafficEngine {
    private SyntheticTrafficEngine() {}

    public static final class BatchResult {
        public int total, normal, automated;
        public double averageDurationSeconds, averageGapSeconds;
    }

    public static BatchResult generate(int count, Random random) {
        BatchResult out = new BatchResult();
        double durationSum = 0, gapSum = 0;
        for (int i = 0; i < count; i++) {
            int profile = random.nextInt(100);
            boolean automated = profile >= 55;
            double duration = automated
                ? (profile < 80 ? 3 + random.nextDouble() * 5 : 1 + random.nextDouble() * 2)
                : 18 + random.nextDouble() * 150;
            double gap = automated
                ? (profile < 80 ? 0.4 + random.nextDouble() * 1.5 : random.nextDouble() * 0.25)
                : 8 + random.nextDouble() * 90;
            out.total++;
            if (automated) out.automated++;
            else out.normal++;
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
