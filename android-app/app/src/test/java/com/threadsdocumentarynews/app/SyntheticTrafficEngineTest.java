package com.threadsdocumentarynews.app;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class SyntheticTrafficEngineTest {
    @Test public void generatedSessionsAreAllAccountedFor() {
        SyntheticTrafficEngine.BatchResult result =
                SyntheticTrafficEngine.generate(500, new Random(7));
        assertEquals(500, result.total);
        assertEquals(500, result.normal + result.automated);
        assertEquals(500, result.truePositive + result.falseNegative
                + result.trueNegative + result.falsePositive);
        assertTrue(result.averageDurationSeconds > 0);
        assertTrue(result.averageGapSeconds >= 0);
    }

    @Test public void metricsStayWithinValidRange() {
        SyntheticTrafficEngine.BatchResult result =
                SyntheticTrafficEngine.generate(1000, new Random(21));
        assertTrue(result.accuracy() >= 0 && result.accuracy() <= 1);
        assertTrue(result.precision() >= 0 && result.precision() <= 1);
        assertTrue(result.recall() >= 0 && result.recall() <= 1);
    }

    @Test public void emptyBatchIsSafe() {
        SyntheticTrafficEngine.BatchResult result =
                SyntheticTrafficEngine.generate(0, new Random(1));
        assertEquals(0, result.total);
        assertEquals(0.0, result.accuracy(), 0.0);
        assertEquals(0.0, result.precision(), 0.0);
        assertEquals(0.0, result.recall(), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeBatchIsRejected() {
        SyntheticTrafficEngine.generate(-1, new Random(1));
    }
}
