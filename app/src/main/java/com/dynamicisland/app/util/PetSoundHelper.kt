package com.dynamicisland.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * PetSoundHelper: Ultra-low-latency, soft, cute programmatic sound effects for Pixel Pet.
 *
 * Characteristics:
 * - 0ms disk/file I/O latency (synthesized directly in memory via AudioTrack PCM)
 * - Soft, comfortable volume levels with individual mute / volume sliders
 * - Gentle, adorable chiptune pet expressions
 */
object PetSoundHelper {

    private val audioExecutor = Executors.newSingleThreadExecutor()
    private const val SAMPLE_RATE = 22050

    fun playHappyChirp(context: Context? = null) {
        val prefs = context?.let { PreferencesHelper(it) }
        if (prefs != null && (!prefs.isSoundEnabled || !prefs.isTapSoundEnabled)) return
        val volume = prefs?.soundVolume ?: 0.55f
        if (volume <= 0.01f) return

        audioExecutor.execute {
            // Cute, sweet ascending chirp: 540Hz -> 860Hz (95ms)
            val durationMs = 95
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)
            val maxAmp = 10000.0 * volume

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val freq = 540.0 + (320.0 * progress)
                val angle = 2.0 * PI * freq * (i.toDouble() / SAMPLE_RATE)
                val envelope = when {
                    progress < 0.20 -> progress / 0.20
                    progress > 0.65 -> (1.0 - progress) / 0.35
                    else -> 1.0
                }
                buffer[i] = (sin(angle) * envelope * maxAmp).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    fun playPunchSqueak(context: Context? = null) {
        val prefs = context?.let { PreferencesHelper(it) }
        if (prefs != null && (!prefs.isSoundEnabled || !prefs.isPunchSoundEnabled)) return
        val volume = prefs?.soundVolume ?: 0.55f
        if (volume <= 0.01f) return

        audioExecutor.execute {
            // Softer, cute plush toy pip: 460Hz -> 380Hz (60ms) - gentle and cuddly, NOT loud!
            val durationMs = 60
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)
            val maxAmp = 8000.0 * volume

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val freq = 460.0 - (80.0 * progress)
                val angle = 2.0 * PI * freq * (i.toDouble() / SAMPLE_RATE)
                val envelope = when {
                    progress < 0.15 -> progress / 0.15
                    else -> (1.0 - progress)
                }
                buffer[i] = (sin(angle) * envelope * maxAmp).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    fun playAngryTantrum(context: Context? = null) {
        val prefs = context?.let { PreferencesHelper(it) }
        if (prefs != null && (!prefs.isSoundEnabled || !prefs.isAngerSoundEnabled)) return
        val volume = prefs?.soundVolume ?: 0.55f
        if (volume <= 0.01f) return

        audioExecutor.execute {
            // Playful gentle rumble: 220Hz -> 260Hz (110ms)
            val durationMs = 110
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)
            val maxAmp = 9500.0 * volume

            for (i in 0 until numSamples) {
                val progress = i.toDouble() / numSamples
                val freq = if (progress < 0.5) 220.0 else 260.0
                val angle = 2.0 * PI * freq * (i.toDouble() / SAMPLE_RATE)
                val subProgress = (progress * 2.0) % 1.0
                val envelope = (1.0 - subProgress) * 0.85
                buffer[i] = (sin(angle) * envelope * maxAmp).toInt().toShort()
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
            Thread.sleep((buffer.size * 1000L / SAMPLE_RATE) + 15L)
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
