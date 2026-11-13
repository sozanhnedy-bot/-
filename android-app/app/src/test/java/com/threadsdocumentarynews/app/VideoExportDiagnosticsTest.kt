package com.threadsdocumentarynews.app

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExportDiagnosticsTest {
    @Test
    fun includesCauseAndSuppressedPrimaryEngineFailure() {
        val error = IllegalStateException("Secondary export failed", IOException("codec initialization failed"))
        error.addSuppressed(IllegalArgumentException("FFmpeg failed during video segment 1: invalid input"))

        val report = VideoExportDiagnostics.format(error)

        assertTrue(report.contains("Secondary export failed"))
        assertTrue(report.contains("codec initialization failed"))
        assertTrue(report.contains("FFmpeg failed during video segment 1"))
    }

    @Test
    fun redactsBearerAndApiKeyValues() {
        val report = VideoExportDiagnostics.format(
            IllegalStateException("Authorization: Bearer abc.def-123 api_key=1234567890abcdef")
        )

        assertFalse(report.contains("abc.def-123"))
        assertFalse(report.contains("1234567890abcdef"))
        assertTrue(report.contains("[محذوف]"))
    }

    @Test
    fun capsVeryLongFfmpegOutput() {
        val report = VideoExportDiagnostics.format(IllegalStateException("x".repeat(20000)))

        assertTrue(report.length <= 12000)
        assertTrue(report.contains("تم اختصار سجل التشخيص"))
    }
}
