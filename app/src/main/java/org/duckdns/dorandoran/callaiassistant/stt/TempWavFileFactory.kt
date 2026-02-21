package org.duckdns.dorandoran.callaiassistant.stt

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

object TempWavFileFactory {
    fun createToneWav(
        cacheDir: File,
        durationMs: Int = 600,
        sampleRate: Int = 16000,
        frequencyHz: Double = 440.0
    ): File {
        val safeDurationMs = durationMs.coerceAtLeast(100)
        val totalSamples = (sampleRate * safeDurationMs) / 1000
        val pcmData = ByteArray(totalSamples * 2)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate.toDouble()
            val sample = (sin(2.0 * PI * frequencyHz * t) * Short.MAX_VALUE * 0.20).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val idx = i * 2
            pcmData[idx] = (sample and 0xFF).toByte()
            pcmData[idx + 1] = ((sample ushr 8) and 0xFF).toByte()
        }

        val file = File.createTempFile("ai_correction_probe_", ".wav", cacheDir)
        writeWav(file, pcmData, sampleRate, channels = 1, bitsPerSample = 16)
        return file
    }

    private fun writeWav(
        file: File,
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val subChunk2Size = pcmData.size
        val chunkSize = 36 + subChunk2Size

        DataOutputStream(FileOutputStream(file)).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLE(chunkSize)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLE(16)
            out.writeShortLE(1)
            out.writeShortLE(channels)
            out.writeIntLE(sampleRate)
            out.writeIntLE(byteRate)
            out.writeShortLE(blockAlign)
            out.writeShortLE(bitsPerSample)
            out.writeBytes("data")
            out.writeIntLE(subChunk2Size)
            out.write(pcmData)
        }
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
        writeByte((value ushr 16) and 0xFF)
        writeByte((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }
}
