package com.threadsdocumentarynews.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticStoreTest {
    @Test public void sanitizeForDiagnosticsRedactsSecrets() {
        String firebaseKey = "AIza012345678901234567890123456789012345678";
        String secret = "debug_token=abcdefghijklmnop api_key=" + firebaseKey + " " + firebaseKey + " Bearer abcdefghijklmnop";
        String sanitized = DiagnosticStore.sanitizeForDiagnostics(secret);

        assertFalse(sanitized.contains("abcdefghijklmnop"));
        assertFalse(sanitized.contains(firebaseKey));
        assertTrue(sanitized.contains("[محذوف]"));
        assertTrue(sanitized.contains("[مفتاح API محذوف]"));
    }
}
