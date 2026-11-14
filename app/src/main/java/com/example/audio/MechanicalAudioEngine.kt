package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.model.KeyResistance
import com.example.data.model.SwitchProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

enum class KeySoundType {
    ALPHA,
    SPACE,
    BACKSPACE,
    RETURN
}

class MechanicalAudioEngine(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Primary media stream for crisp, full-dynamic-range mechanical keystrokes
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private data class SoundKey(
        val profile: SwitchProfile,
        val keyType: KeySoundType,
        val variant: Int
    )

    private val soundIds = mutableMapOf<SoundKey, Int>()
    private var lastAlphaVariant = -1
    @Volatile
    private var isLoaded = false

    init {
        scope.launch {
            generateAndLoadSounds()
        }
    }

    private fun generateAndLoadSounds() {
        val dir = File(appContext.cacheDir, "keystroke_sfx_v3").apply { mkdirs() }
        SwitchProfile.entries.forEach { profile ->
            KeySoundType.entries.forEach { keyType ->
                val variantCount = if (keyType == KeySoundType.ALPHA) ALPHA_VARIANTS else 1
                for (variant in 0 until variantCount) {
                    val file = File(dir, "${profile.id}_${keyType.name.lowercase()}_$variant.wav")
                    if (!file.exists() || file.length() < 44L) {
                        val samples = synthesizeKeySample(profile, keyType, variant)
                        FileOutputStream(file).use { out ->
                            out.write(encodeWav(samples, SAMPLE_RATE))
                        }
                    }
                    val id = soundPool.load(file.absolutePath, 1)
                    soundIds[SoundKey(profile, keyType, variant)] = id
                }
            }
        }
        isLoaded = true
    }

    fun playKeystroke(
        keyType: KeySoundType,
        profile: SwitchProfile,
        resistance: KeyResistance,
        volume: Float,
        hapticsEnabled: Boolean
    ) {
        if (hapticsEnabled) {
            triggerHaptic(keyType, resistance)
        }
        if (!isLoaded || volume <= 0.01f) return

        val variant = if (keyType == KeySoundType.ALPHA) {
            var next = Random.nextInt(ALPHA_VARIANTS)
            if (next == lastAlphaVariant) next = (next + 1) % ALPHA_VARIANTS
            lastAlphaVariant = next
            next
        } else {
            0
        }

        val soundId = soundIds[SoundKey(profile, keyType, variant)] ?: return
        val baseRate = when (resistance) {
            KeyResistance.LIGHT_35G -> 1.04f
            KeyResistance.TACTILE_55G -> 1.0f
            KeyResistance.HEAVY_75G -> 0.95f
            KeyResistance.VINTAGE_90G -> 0.90f
        }
        val jitter = 0.975f + Random.nextFloat() * 0.05f
        val rate = (baseRate * jitter).coerceIn(0.70f, 1.28f)
        val clampedVol = volume.coerceIn(0f, 1f)
        soundPool.play(soundId, clampedVol, clampedVol, 1, 0, rate)
    }

    private fun triggerHaptic(keyType: KeySoundType, resistance: KeyResistance) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val durationMultiplier = when (keyType) {
            KeySoundType.ALPHA -> 1.0f
            KeySoundType.BACKSPACE -> 0.9f
            KeySoundType.SPACE -> 1.25f
            KeySoundType.RETURN -> 1.55f
        }
        val duration = (resistance.hapticDurationMs * durationMultiplier).toLong().coerceIn(6L, 65L)
        val amplitude = when (keyType) {
            KeySoundType.RETURN -> (resistance.hapticAmplitude + 25).coerceAtMost(255)
            KeySoundType.SPACE -> (resistance.hapticAmplitude + 10).coerceAtMost(255)
            else -> resistance.hapticAmplitude
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(duration)
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        soundPool.release()
    }

    /**
     * State-variable bandpass filter for shaping dry broadband white noise into
     * authentic wooden/rubber/steel acoustic mechanical transients without pure-sine beeping.
     */
    private class StateVariableBandpass(centerHz: Double, private val q: Double) {
        private val f = (2.0 * sin(PI * centerHz / SAMPLE_RATE)).coerceIn(0.01, 0.95)
        private var low = 0.0
        private var band = 0.0

        fun process(input: Double): Double {
            low += f * band
            val high = input - low - (band / q)
            band += f * high
            return band
        }
    }

    private fun synthesizeKeySample(
        profile: SwitchProfile,
        keyType: KeySoundType,
        variant: Int
    ): ShortArray {
        val durationMs = when (keyType) {
            KeySoundType.RETURN -> 440
            KeySoundType.SPACE -> 125
            else -> 105
        }
        val count = (SAMPLE_RATE * durationMs) / 1000
        val raw = DoubleArray(count)
        val rng = Random(profile.ordinal * 131 + keyType.ordinal * 37 + variant * 19 + 7)
        val variantScale = 0.94 + variant * 0.045

        // Acoustic resonator banks
        val linkageFilter = StateVariableBandpass(1850.0 * variantScale, q = 1.6)
        val platenSnapFilter = StateVariableBandpass(1280.0 * variantScale, q = 2.0)
        val chassisFilter = StateVariableBandpass(540.0 * variantScale, q = 2.4)
        val returnFilter = StateVariableBandpass(980.0, q = 2.2)

        var phaseThud = 0.0
        var lowPassPrev = 0.0

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val white = rng.nextDouble() * 2.0 - 1.0

            val sample = when (keyType) {
                KeySoundType.RETURN -> {
                    // Authentic carriage return: fast gear-train ratchet zip + crisp cast bell + stop thump
                    var ratchet = 0.0
                    for (notch in 0 until 6) {
                        val dt = t - notch * 0.015
                        if (dt in 0.0..0.020) {
                            val env = exp(-dt * 240.0)
                            ratchet += returnFilter.process(white) * env * 0.9 +
                                sin(2.0 * PI * (480.0 + notch * 55.0) * dt) * env * 0.35
                        }
                    }
                    val bellStart = (t - 0.050).coerceAtLeast(0.0)
                    val bellEnv = if (t >= 0.050) exp(-bellStart * 10.5) else 0.0
                    val bell = (sin(2.0 * PI * 2180.0 * bellStart) * 0.60 +
                        sin(2.0 * PI * 4370.0 * bellStart) * 0.25 +
                        sin(2.0 * PI * 6540.0 * bellStart) * 0.10) * bellEnv
                    val stopThump = if (t >= 0.095) {
                        val dt = t - 0.095
                        val thudF = 110.0 + 140.0 * exp(-dt * 120.0)
                        sin(2.0 * PI * thudF * dt) * exp(-dt * 48.0) * 1.1 +
                            chassisFilter.process(white) * exp(-dt * 95.0) * 0.7
                    } else 0.0
                    ratchet * 0.75 + bell * 0.95 + stopThump
                }

                KeySoundType.SPACE -> {
                    // Wide mechanical spacebar lever & escapement bar clunk
                    val thumpFreq = when (profile) {
                        SwitchProfile.TOPRE_THOCK -> 92.0
                        SwitchProfile.VINTAGE_OLIVETTI -> 132.0
                        else -> 112.0
                    }
                    val freq = thumpFreq + 160.0 * exp(-t * 160.0)
                    phaseThud += 2.0 * PI * freq / SAMPLE_RATE
                    val mechClick = linkageFilter.process(white) * exp(-t * 380.0) * 0.85
                    val barThump = sin(phaseThud) * exp(-t * 44.0) * 1.15
                    val barSnap = if (t >= 0.008) {
                        val dt = t - 0.008
                        platenSnapFilter.process(white) * exp(-dt * 145.0) * 0.95 +
                            chassisFilter.process(white) * exp(-dt * 85.0) * 0.85
                    } else 0.0
                    mechClick + barThump + barSnap
                }

                KeySoundType.BACKSPACE -> {
                    // Crisp escapement pawl step-back click + short thud
                    val pawlFreq = 160.0 + 210.0 * exp(-t * 190.0)
                    phaseThud += 2.0 * PI * pawlFreq / SAMPLE_RATE
                    val pawlSnap = linkageFilter.process(white) * exp(-t * 260.0) * 1.05
                    val body = sin(phaseThud) * exp(-t * 70.0) * 0.90
                    val settle = if (t >= 0.016) {
                        val dt = t - 0.016
                        chassisFilter.process(white) * exp(-dt * 190.0) * 0.6
                    } else 0.0
                    pawlSnap + body + settle
                }

                KeySoundType.ALPHA -> when (profile) {
                    SwitchProfile.VINTAGE_OLIVETTI -> {
                        // Original manual typewriter:
                        // 1. Key-lever cam release tick (0ms)
                        val leverDelay = 0.0
                        val leverTick = linkageFilter.process(white) * exp(-t * 420.0) * 0.90
                        // 2. Typebar metal slug slapping ribbon & hard rubber platen roller (10ms)
                        val strikeTime = 0.010 + variant * 0.0012
                        val strike = if (t >= strikeTime) {
                            val dt = t - strikeTime
                            val platenFreq = (128.0 + 240.0 * exp(-dt * 185.0)) * variantScale
                            phaseThud += 2.0 * PI * platenFreq / SAMPLE_RATE
                            val rubberThump = sin(phaseThud) * exp(-dt * 72.0) * 1.15
                            val ribbonSnap = platenSnapFilter.process(white) * exp(-dt * 235.0) * 1.45
                            val woodFrame = chassisFilter.process(white) * exp(-dt * 92.0) * 0.95
                            rubberThump + ribbonSnap + woodFrame
                        } else 0.0
                        // 3. Typebar rebound / basket rest tick (27ms)
                        val reboundTime = 0.027 + variant * 0.0015
                        val rebound = if (t >= reboundTime) {
                            val dt = t - reboundTime
                            linkageFilter.process(white) * exp(-dt * 300.0) * 0.42 +
                                chassisFilter.process(white) * exp(-dt * 140.0) * 0.35
                        } else 0.0
                        leverTick + strike + rebound + leverDelay
                    }

                    SwitchProfile.CHERRY_BLUE -> {
                        // Crisp click-jacket tactile snap + bottom-out
                        val click1 = linkageFilter.process(white) * exp(-t * 360.0) * 1.35
                        val bottomTime = 0.008
                        val bottom = if (t >= bottomTime) {
                            val dt = t - bottomTime
                            val f = 145.0 + 180.0 * exp(-dt * 190.0)
                            phaseThud += 2.0 * PI * f / SAMPLE_RATE
                            sin(phaseThud) * exp(-dt * 75.0) * 0.95 +
                                platenSnapFilter.process(white) * exp(-dt * 250.0) * 1.05
                        } else 0.0
                        click1 + bottom
                    }

                    SwitchProfile.TACTILE_BROWN -> {
                        val f = 125.0 + 170.0 * exp(-t * 170.0)
                        phaseThud += 2.0 * PI * f / SAMPLE_RATE
                        val bump = platenSnapFilter.process(white) * exp(-t * 250.0) * 0.95
                        val thump = sin(phaseThud) * exp(-t * 65.0) * 1.05 +
                            chassisFilter.process(white) * exp(-t * 115.0) * 0.75
                        bump + thump
                    }

                    SwitchProfile.TOPRE_THOCK -> {
                        val f = 92.0 + 125.0 * exp(-t * 145.0)
                        phaseThud += 2.0 * PI * f / SAMPLE_RATE
                        val thock = sin(phaseThud) * exp(-t * 48.0) * 1.25
                        val housing = chassisFilter.process(white) * exp(-t * 110.0) * 0.85
                        thock + housing
                    }
                }
            }

            // Gentle warm low-pass rolloff so phone speakers deliver woody punch without shrill hiss
            val warm = 0.70 * sample + 0.30 * lowPassPrev
            lowPassPrev = warm
            raw[i] = tanh(warm * 1.55)
        }

        val peak = raw.maxOfOrNull { abs(it) }?.coerceAtLeast(0.01) ?: 1.0
        val scale = 31200.0 / peak
        return ShortArray(count) { i ->
            (raw[i] * scale).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun encodeWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val byteBuffer = ByteArrayOutputStream(44 + dataSize)
        fun writeStr(s: String) = byteBuffer.write(s.toByteArray(Charsets.US_ASCII))
        fun writeIntLE(v: Int) {
            byteBuffer.write(v and 0xFF)
            byteBuffer.write((v shr 8) and 0xFF)
            byteBuffer.write((v shr 16) and 0xFF)
            byteBuffer.write((v shr 24) and 0xFF)
        }
        fun writeShortLE(v: Int) {
            byteBuffer.write(v and 0xFF)
            byteBuffer.write((v shr 8) and 0xFF)
        }

        writeStr("RIFF")
        writeIntLE(36 + dataSize)
        writeStr("WAVE")
        writeStr("fmt ")
        writeIntLE(16)
        writeShortLE(1)
        writeShortLE(1)
        writeIntLE(sampleRate)
        writeIntLE(sampleRate * 2)
        writeShortLE(2)
        writeShortLE(16)
        writeStr("data")
        writeIntLE(dataSize)
        for (s in samples) {
            writeShortLE(s.toInt())
        }
        return byteBuffer.toByteArray()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val ALPHA_VARIANTS = 4
    }
}
