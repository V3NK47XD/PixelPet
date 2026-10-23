package com.dynamicisland.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * PetSoundHelper: Ultra-low-latency programmatic sound effects for Pixel Pet.
 *
 * Uses native AudioTrack with PCM sine synthesis:
 * - 0ms disk/file I/O latency (synthesized directly in memory)
 * - 100% offline, zero assets needed
 * - Playful 8-bit / chiptune pet expressions
 */
object PetSoundHelper {

    private val audioExecutor = Executors.newSingleThreadExecutor()
    private const val SAMPLE_RATE = 22050

    fun playHappyChirp() {
        audioExecutor.execute {
            // Cheerful ascending chirp: 520Hz -> 920Hz (110ms)
            val durationMs = 110
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val freq = 520.0 + (400.0 * progress)
                val angle = 2.0 * PI * freq * (i.toDouble() / SAMPLE_RATE)
                // Smooth attack and decay envelope
                val envelope = when {
                    progress < 0.15 -> progress / 0.15
                    progress > 0.70 -> (1.0 - progress) / 0.30
                    else -> 1.0
                }
                buffer[i] = (sin(angle) * envelope * 24000.0).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    fun playPunchSqueak() {
        audioExecutor.execute {
            // Startled flinch squeak: 750Hz -> 340Hz (85ms)
            val durationMs = 85
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val freq = 750.0 - (410.0 * progress * progress)
                val angle = 2.0 * PI * freq * (i.toDouble() / SAMPLE_RATE)
                val envelope = when {
                    progress < 0.1 -> progress / 0.1
                    else -> (1.0 - progress)
                }
                buffer[i] = (sin(angle) * envelope * 26000.0).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    fun playAngryTantrum() {
        audioExecutor.execute {
            // Angry rapid double grunt: 240Hz & 320Hz (140ms)
            val durationMs = 140
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val freq = if (progress < 0.5) 240.0 else 320.0
                val angle = 2.0 * PI * freq * (i.toDouble() / SAMPLE_RATE)
                val subProgress = (progress * 2.0) % 1.0
                val envelope = (1.0 - subProgress) * 0.9
                buffer[i] = (sin(angle) * envelope * 28000.0).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        var track: AudioTrack? = null
        try {
            val bufferSize = buffer.size * 2
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            track = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep((buffer.size * 1000L / SAMPLE_RATE) + 20L)
        } catch (e: Exception) {
            // Non-critical: sound fails silently if audio system is restricted
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (ignored: Exception) {}
        }
    }
}
