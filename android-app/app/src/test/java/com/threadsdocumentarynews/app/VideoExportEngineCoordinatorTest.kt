package com.threadsdocumentarynews.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExportEngineCoordinatorTest {
    @Test
    fun progressMappingClampsInputsAndKeepsFallbackInRemainingRange() {
        assertEquals(0, VideoExportEngineCoordinator.mapProgress(-10, 0, 80))
        assertEquals(40, VideoExportEngineCoordinator.mapProgress(50, 0, 80))
        assertEquals(40, VideoExportEngineCoordinator.mapProgress(0, 40, 60))
        assertEquals(100, VideoExportEngineCoordinator.mapProgress(100, 40, 60))
        assertEquals(100, VideoExportEngineCoordinator.mapProgress(100, 100, 100))
    }

    @Test
    fun onlyNonEmptyFilesAreUsableExportOutputs() {
        val directory = Files.createTempDirectory("news-export-test").toFile()
        try {
            val empty = File(directory, "empty.mp4")
            empty.createNewFile()
            val valid = File(directory, "valid.mp4")
            valid.writeBytes(byteArrayOf(1, 2, 3))
            assertFalse(VideoExportEngineCoordinator.isUsableOutput(empty))
            assertTrue(VideoExportEngineCoordinator.isUsableOutput(valid))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun promotionIsSameDirectoryAndDoesNotOverwriteAnExistingSuccessfulFile() {
        val directory = Files.createTempDirectory("news-promote-test").toFile()
        try {
            val temporary = File(directory, "temp.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val target = File(directory, "final.mp4")
            assertTrue(VideoExportEngineCoordinator.promote(temporary, target))
            assertFalse(temporary.exists())
            assertTrue(VideoExportEngineCoordinator.isUsableOutput(target))

            val secondTemporary = File(directory, "second.mp4").apply { writeBytes(byteArrayOf(4)) }
            assertFalse(VideoExportEngineCoordinator.promote(secondTemporary, target))
            assertTrue(secondTemporary.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
