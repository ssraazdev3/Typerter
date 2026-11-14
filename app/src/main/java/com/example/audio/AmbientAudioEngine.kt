package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.model.AmbientTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AmbientAudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null
    private var currentTrack: AmbientTrack = AmbientTrack.NONE
    @Volatile
    private var targetVolume: Float = 0.45f

    fun update(track: AmbientTrack, volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        if (track == currentTrack && playJob?.isActive == true) return
        currentTrack = track
        playJob?.cancel()
        if (track == AmbientTrack.NONE) return

        playJob = scope.launch {
            streamTrack(track)
        }
    }

    fun release() {
        playJob?.cancel()
        playJob = null
    }

    private fun CoroutineScope.streamTrack(track: AmbientTrack) {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            return
        }

        val buffer = ShortArray(2048)
        val rng = Random(track.ordinal * 997 + 13)
        var lp1 = 0.0
        var lp2 = 0.0
        var brown = 0.0
        var phase = 0.0
        var dropAmp = 0.0
        var dropFreq = 1200.0
        var dropPhase = 0.0
        var crackleAmp = 0.0
        var smoothGain = 0.0

        try {
            audioTrack.play()
            while (isActive) {
                for (i in buffer.indices) {
                    smoothGain += (targetVolume - smoothGain) * 0.002
                    val white = rng.nextDouble() * 2.0 - 1.0
                    brown = (brown + white * 0.045).coerceIn(-1.0, 1.0) * 0.996

                    val rawSample = when (track) {
                        AmbientTrack.SNOWFALL -> {
                            phase += 1.0 / sampleRate
                            val lfo = 0.65 + 0.35 * sin(2.0 * PI * 0.09 * phase)
                            val pad = (
                                sin(2.0 * PI * 110.0 * phase) * 0.30 +
                                    sin(2.0 * PI * 164.81 * phase) * 0.22 +
                                    sin(2.0 * PI * 220.0 * phase) * 0.18 +
                                    sin(2.0 * PI * 246.94 * phase) * 0.14 +
                                    sin(2.0 * PI * 329.63 * phase) * 0.10
                                ) * lfo
                            lp1 += (white * 0.12 - lp1) * 0.035
                            pad * 0.36 + lp1 * 0.25
                        }
                        AmbientTrack.RAIN -> {
                            lp1 += (white - lp1) * 0.16
                            lp2 += (lp1 - lp2) * 0.18
                            if (rng.nextInt(3200) == 0) {
                                dropAmp = 0.16 + rng.nextDouble() * 0.18
                                dropFreq = 750.0 + rng.nextDouble() * 1400.0
                                dropPhase = 0.0
                            }
                            dropPhase += dropFreq / sampleRate
                            val drop = sin(2.0 * PI * dropPhase) * dropAmp
                            dropAmp *= 0.9965
                            lp2 * 0.55 + brown * 0.28 + drop
                        }
                        AmbientTrack.FIREPLACE -> {
                            lp1 += (brown - lp1) * 0.08
                            if (rng.nextInt(1800) == 0) {
                                crackleAmp = 0.28 + rng.nextDouble() * 0.35
                            }
                            val crackle = (rng.nextDouble() * 2.0 - 1.0) * crackleAmp
                            crackleAmp *= 0.985
                            lp1 * 0.85 + crackle * 0.42
                        }
                        AmbientTrack.CAFE -> {
                            phase += 1.0 / sampleRate
                            lp1 += (brown - lp1) * 0.12
                            val mod = 0.75 + 0.25 * sin(2.0 * PI * 0.27 * phase)
                            if (rng.nextInt(9000) == 0) {
                                dropAmp = 0.10
                                dropFreq = 2100.0 + rng.nextDouble() * 700.0
                                dropPhase = 0.0
                            }
                            dropPhase += dropFreq / sampleRate
                            val clink = sin(2.0 * PI * dropPhase) * dropAmp
                            dropAmp *= 0.997
                            lp1 * 0.65 * mod + clink * 0.4
                        }
                        AmbientTrack.NONE -> 0.0
                    }

                    val scaled = (rawSample * smoothGain * 14000.0)
                        .toInt()
                        .coerceIn(-32767, 32767)
                        .toShort()
                    buffer[i] = scaled
                }
                audioTrack.write(buffer, 0, buffer.size)
            }
        } catch (_: Exception) {
            // AudioTrack interrupted on coroutine cancel
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {
            }
        }
    }
}
