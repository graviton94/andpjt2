package com.graviton94.notificationcleaner

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

internal class ImpactFeedback(private val context: Context) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val sound = SoundPool.Builder().setAudioAttributes(attributes).setMaxStreams(5).build()
    private val swipe = sound.load(context, R.raw.impact_release, 1)
    private val merge = sound.load(context, R.raw.impact_merge, 1)
    private val burst = sound.load(context, R.raw.impact_burst, 1)
    private val overflow = sound.load(context, R.raw.impact_overflow, 1)
    private val finish = sound.load(context, R.raw.impact_collapse, 1)
    private val tap = sound.load(context, R.raw.impact_tap, 1)

    @Suppress("DEPRECATION")
    private val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else context.getSystemService(Vibrator::class.java)
    private var vibrating = false
    private val streams = ArrayDeque<Int>()

    fun threshold(view: View, panel: PanelSettings) {
        if (panel.quiet) return
        if (panel.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (panel.sound) play(tap, .28f, .28f, 1, 1.16f)
    }

    fun strike(view: View, strike: Strike, panel: PanelSettings) {
        if (panel.quiet) return
        if (panel.sound) {
            val sample = when (strike.kind) {
                ImpactKind.Swipe -> swipe
                ImpactKind.Merge -> merge
                ImpactKind.Burst -> burst
                ImpactKind.Overflow -> overflow
                ImpactKind.Finish -> finish
            }
            val volume = if (strike.manual) .70f else .43f
            val rate = when (strike.kind) {
                ImpactKind.Overflow -> (.88f + strike.frame.combo * .035f).coerceAtMost(1.5f)
                ImpactKind.Burst -> .86f + strike.frame.combo * .06f
                else -> 1f
            }
            val left = if (strike.direction < 0) volume else volume * .78f
            val right = if (strike.direction > 0) volume else volume * .78f
            play(sample, left, right, strike.tier + 1, rate)
        }
        if (!panel.haptics) return
        when (strike.kind) {
            ImpactKind.Swipe -> if (strike.manual) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            ImpactKind.Merge -> if (strike.manual) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            ImpactKind.Burst -> pulse(
                longArrayOf(0, 17, 13, 23),
                intArrayOf(0, 124, 0, 194),
            )
            ImpactKind.Overflow -> pulse(
                longArrayOf(0, 25),
                intArrayOf(0, (126 + strike.frame.combo * 9).coerceAtMost(245)),
            )
            ImpactKind.Finish -> pulse(
                longArrayOf(0, 33, 29, 59, 47, 43),
                intArrayOf(0, 255, 0, 224, 0, 143),
            )
        }
    }

    private fun play(sample: Int, left: Float, right: Float, priority: Int, rate: Float) {
        val playback = sound.play(sample, left, right, priority, 0, rate)
        if (playback == 0) return
        if (streams.size >= 12) streams.removeFirst()
        streams.addLast(playback)
    }

    fun stopEffects() {
        streams.forEach(sound::stop)
        streams.clear()
        stopVibration()
    }

    fun stopVibration() {
        if (vibrating) vibrator?.cancel()
        vibrating = false
    }

    @Suppress("DEPRECATION")
    private fun pulse(durations: LongArray, strengths: IntArray) {
        val motor = vibrator ?: return
        if (!motor.hasVibrator() || Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        val vibration = if (motor.hasAmplitudeControl()) VibrationEffect.createWaveform(durations, strengths, -1)
            else VibrationEffect.createWaveform(durations, -1)
        motor.vibrate(vibration)
        vibrating = true
    }

    fun release() {
        stopVibration()
        sound.release()
    }
}
