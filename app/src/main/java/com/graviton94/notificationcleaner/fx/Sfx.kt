package com.graviton94.notificationcleaner.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator

/** SoundPool over the generated id table; same-id repeats within 30 ms merge (20 sound §0). */
class Sfx(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(24)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val loaded = SOUND_IDS.mapValues { (_, res) -> pool.load(context, res, 1) }
    private val last = HashMap<String, Long>()
    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    /** Volume by weight ladder: phone pings sit under the hits. */
    private fun base(id: String) = when {
        id.startsWith("notify.") -> 0.25f
        id.startsWith("ui.") || id.startsWith("phone.") -> 0.5f
        id.startsWith("chip") || id == "swipe" || id == "merge" -> 0.6f
        else -> 0.9f
    }

    fun play(id: String, volume: Float = 1f) {
        val sound = loaded[id] ?: return
        val t = System.currentTimeMillis()
        if (t - (last[id] ?: 0L) < 30) return
        last[id] = t
        val v = (base(id) * volume).coerceIn(0f, 1f)
        pool.play(sound, v, v, 1, 0, 1f)
    }

    fun buzz(ms: Long) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms.coerceIn(5, 60), VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    fun release() = pool.release()
}
