package net.maz.llamachat.data.attach

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records the mic to a PCM16 WAV file via [AudioRecord]. llama-server only takes
 * wav/mp3 audio and [MediaRecorder] can't emit either, so raw PCM plus a
 * hand-written RIFF header is the whole story. 16 kHz mono keeps voice clips
 * small (~32 KB/s raw, ~43 KB/s as base64).
 */
class WavRecorder {

    private var record: AudioRecord? = null
    private var writer: Thread? = null
    @Volatile private var running = false
    private var outFile: File? = null

    /**
     * Start capturing into [file]. Returns false when the recorder can't be set
     * up (mic busy, missing permission). The caller must hold RECORD_AUDIO.
     */
    @SuppressLint("MissingPermission")
    fun start(file: File): Boolean {
        if (running) return false
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return false
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, minBuf * 4)
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        record = rec
        outFile = file
        running = true
        rec.startRecording()
        writer = Thread {
            file.outputStream().use { out ->
                out.write(ByteArray(HEADER_SIZE)) // placeholder, patched in stop()
                val buf = ByteArray(minBuf)
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) out.write(buf, 0, n)
                }
            }
        }.apply { start() }
        return true
    }

    /** Stop, patch the WAV header, and return the clip duration in ms (0 if idle/empty). */
    fun stop(): Long {
        if (!running) return 0L
        running = false
        writer?.join(1_000)
        writer = null
        record?.let { rec ->
            runCatching { rec.stop() }
            rec.release()
        }
        record = null
        val file = outFile ?: return 0L
        outFile = null
        val dataSize = (file.length() - HEADER_SIZE).coerceAtLeast(0)
        writeHeader(file, dataSize)
        // PCM16 mono: 2 bytes per sample.
        return dataSize * 1_000 / (SAMPLE_RATE * 2)
    }

    /** Idempotent teardown for onCleared; discards nothing (the file stays patched). */
    fun release() {
        if (running) stop()
    }

    private fun writeHeader(file: File, dataSize: Long) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((dataSize + 36).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM fmt chunk size
        header.putShort(1) // audio format: PCM
        header.putShort(1) // channels: mono
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2) // byte rate
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize.toInt())
        runCatching {
            RandomAccessFile(file, "rw").use {
                it.seek(0)
                it.write(header.array())
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val HEADER_SIZE = 44
    }
}
