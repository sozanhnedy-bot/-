package com.threadsdocumentarynews.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiSpeechTest {
    @Test
    fun wrapsPcmInMono24Khz16BitWav() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = GeminiSpeech.pcmToWav(pcm)
        assertEquals(48, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(40, header.getInt(4))
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(24_000, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(4, header.getInt(40))
    }

    @Test
    fun rejectsOddPcmByteCount() {
        assertThrows(IllegalArgumentException::class.java) {
            GeminiSpeech.pcmToWav(byteArrayOf(1, 2, 3))
        }
    }
}
