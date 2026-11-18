package com.threadsdocumentarynews.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class VisitDetectionEngineTest {
    @Test public void ordinaryLookingSessionIsNotFlagged() {
        VisitDetectionEngine.Session session = new VisitDetectionEngine.Session(
                74, 32, 1, 0.10, false);
        VisitDetectionEngine.Result result = VisitDetectionEngine.analyze(session);
        assertFalse(result.suspicious);
        assertEquals(0, result.riskScore);
    }

    @Test public void multipleAutomationSignalsAreFlagged() {
        VisitDetectionEngine.Session session = new VisitDetectionEngine.Session(
                2, 0.4, 12, 0.95, true);
        VisitDetectionEngine.Result result = VisitDetectionEngine.analyze(session);
        assertTrue(result.suspicious);
        assertEquals(100, result.riskScore);
        assertTrue(result.signals.contains("BURST_INTERVAL"));
        assertTrue(result.signals.contains("AUTOMATION_INDICATOR"));
    }

    @Test public void batchReportCountsResults() {
        VisitDetectionEngine.Session normal = new VisitDetectionEngine.Session(
                60, 20, 1, 0.2, false);
        VisitDetectionEngine.Session suspicious = new VisitDetectionEngine.Session(
                3, 0.5, 8, 0.9, false);
        VisitDetectionEngine.BatchReport report =
                VisitDetectionEngine.analyzeBatch(Arrays.asList(normal, suspicious));
        assertEquals(2, report.total);
        assertEquals(1, report.normal);
        assertEquals(1, report.suspicious);
        assertEquals(37.5, report.averageRiskScore, 0.001);
    }

    @Test public void emptyBatchHasZeroAverage() {
        VisitDetectionEngine.BatchReport report =
                VisitDetectionEngine.analyzeBatch(Collections.<VisitDetectionEngine.Session>emptyList());
        assertEquals(0, report.total);
        assertEquals(0, report.averageRiskScore, 0.0);
    }
}
