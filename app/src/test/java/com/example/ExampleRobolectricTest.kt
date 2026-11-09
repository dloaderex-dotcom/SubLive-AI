package com.example

import com.example.sublive.audio.AudioConfig
import com.example.sublive.audio.AudioLevelMeter
import com.example.sublive.audio.AudioRingBuffer
import com.example.sublive.engine.SubtitleStabilizer
import com.example.sublive.engine.WhisperVocab
import com.example.sublive.model.ModelTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPipelineTest {

    @Test
    fun testRingBufferWriteAndReadLatest() {
        val ringBuffer = AudioRingBuffer(capacity = 100)
        val data = ShortArray(60) { it.toShort() }
        ringBuffer.write(data)

        assertEquals(60, ringBuffer.available())
        assertEquals(60L, ringBuffer.totalSamplesWritten)

        // Read latest 20 samples
        val out = ShortArray(20)
        val readCount = ringBuffer.readLatest(out, 20)
        assertEquals(20, readCount)
        // Last sample written was 59, so out should contain [40..59]
        assertEquals(40.toShort(), out[0])
        assertEquals(59.toShort(), out[19])
        assertEquals(60, ringBuffer.available())
    }

    @Test
    fun testRingBufferWrapAroundOverflow() {
        val ringBuffer = AudioRingBuffer(capacity = 50)
        val chunk1 = ShortArray(40) { it.toShort() }
        val chunk2 = ShortArray(30) { (it + 40).toShort() }

        ringBuffer.write(chunk1)
        ringBuffer.write(chunk2) // Total 70 samples, capacity 50 -> 20 dropped

        assertEquals(50, ringBuffer.available())
        assertEquals(20L, ringBuffer.droppedSamplesCount)

        val out = ShortArray(10)
        ringBuffer.readLatest(out, 10)
        assertEquals(60.toShort(), out[0])
        assertEquals(69.toShort(), out[9])
    }

    @Test
    fun testAudioLevelMeterZeroSignal() {
        val zeroSamples = ShortArray(1600) { 0 }
        val level = AudioLevelMeter.analyze(zeroSamples, zeroSamples.size)

        assertTrue(level.isZeroSignal)
        assertFalse(level.isSpeechLikely)
        assertEquals(0f, level.rms, 0.001f)
        assertEquals(-96f, level.decibels, 0.001f)
    }

    @Test
    fun testAudioLevelMeterSpeechSignal() {
        // 440Hz test sine wave at 16kHz
        val samples = ShortArray(1600) { i ->
            (16000 * kotlin.math.sin(2.0 * Math.PI * 440.0 * i / AudioConfig.SAMPLE_RATE)).toInt().toShort()
        }
        val level = AudioLevelMeter.analyze(samples, samples.size)

        assertFalse(level.isZeroSignal)
        assertTrue(level.isSpeechLikely)
        assertTrue("RMS should be > 0.1", level.rms > 0.1f)
        assertTrue("dB should be > -20dB", level.decibels > -20f)
    }

    @Test
    fun testSubtitleStabilizerDeduplication() {
        val stabilizer = SubtitleStabilizer()

        // Pass 1: Partial utterance
        val state1 = stabilizer.processWindowResult(
            rawText = "Welcome to real-time speech",
            isSpeechDetected = true
        )
        assertNotNull(state1.currentPartial)
        assertEquals("Welcome to real-time speech", state1.currentPartial?.displayText)

        // Pass 2: Overlapping window extending utterance with sentence ending
        val state2 = stabilizer.processWindowResult(
            rawText = "Welcome to real-time speech transcription.",
            isSpeechDetected = true
        )
        assertEquals(1, state2.finalizedHistory.size)
        assertEquals("Welcome to real-time speech transcription.", state2.finalizedHistory[0].displayText)
    }

    @Test
    fun testWhisperVocabLookup() {
        val word = WhisperVocab.lookup(256)
        // Token lookup returns clean string or null when uninitialized
        assertNull(word)
    }

    @Test
    fun testModelTiersDefinitionsAreTflite() {
        val tiers = ModelTier.entries
        assertEquals(3, tiers.size)
        assertTrue(tiers.all { it.fileName.endsWith(".tflite") })
        assertTrue(tiers.any { it.id == "whisper_tiny_tflite" })
        assertTrue(tiers.any { it.id == "whisper_base_tflite" })
        assertTrue(tiers.any { it.id == "whisper_small_tflite" })
    }
}
