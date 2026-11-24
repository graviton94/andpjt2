package com.graviton94.notificationcleaner.fx

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.graviton94.notificationcleaner.core.Beat
import com.graviton94.notificationcleaner.core.CueKind
import com.graviton94.notificationcleaner.core.FxPlan
import com.graviton94.notificationcleaner.core.Row
import com.graviton94.notificationcleaner.core.SwipeOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var rot: Float, val vr: Float, val size: Float, val color: Color,
    var life: Float, val maxLife: Float, val shard: Boolean,
)

/**
 * Plays a swipe's [FxPlan] timeline (22 §4): holds the old board until the payoff so the bursts happen one by one,
 * hit-stop freezes the world, shards fly, the score layer shows chips × mult, then counts up and slams.
 * Everything the screen draws is read from here; [tick] is driven by the frame clock.
 */
class FxDirector(private val sfx: Sfx, private val scope: CoroutineScope, private val soundOn: () -> Boolean) {
    // board
    var frozenRows by mutableStateOf<List<Row>?>(null); private set
    var walletShown by mutableStateOf<BigInteger?>(null); private set
    val bursting = mutableStateMapOf<Long, Long>()
    val bumped = mutableStateMapOf<Long, Long>()
    val bounds = HashMap<Long, Rect>()
    var origin = Offset.Zero

    // world layer
    val particles = ArrayList<Particle>()
    var frame by mutableLongStateOf(0L); private set
    var shakeX by mutableFloatStateOf(0f); private set
    var shakeY by mutableFloatStateOf(0f); private set
    var flash by mutableFloatStateOf(0f); private set
    var crack by mutableFloatStateOf(0f); private set
    var crackSeed by mutableIntStateOf(0); private set
    var collapse by mutableFloatStateOf(0f); private set

    // score layer (never shaken, never cracked)
    var chips by mutableStateOf(BigInteger.ZERO); private set
    var mult by mutableIntStateOf(0); private set
    var label by mutableStateOf(""); private set
    var labelAt by mutableLongStateOf(-1L); private set
    var labelHot by mutableStateOf(false); private set
    var totalShown by mutableStateOf<BigInteger?>(null); private set
    var slamAt by mutableLongStateOf(-1L); private set

    private var shakeAmp = 0f
    private var freezeUntil = 0L
    private var slowUntil = 0L
    private var collapseAt = -1L
    private var collapsePower = 1f
    private var countFrom = -1L
    private var countTarget = BigInteger.ZERO
    private var last = 0L
    private val rnd = Random(7)
    private var job: Job? = null

    val busy get() = job?.isActive == true
    fun now() = SystemClock.uptimeMillis()

    fun run(o: SwipeOutcome, prevRows: List<Row>, prevWallet: BigInteger, quiet: Boolean) {
        finish()
        val plan = FxPlan.plan(o)
        val bursts = o.beats.filterIsInstance<Beat.Burst>()
        if (o.chain > 0) {
            val swiped = o.beats.filterIsInstance<Beat.Swiped>().map { it.row.id }.toSet()
            frozenRows = prevRows.filter { it.id !in swiped }
            walletShown = prevWallet
        }
        job = scope.launch {
            var at = 0L
            for (cue in plan) {
                if (cue.atMs > at) { delay(cue.atMs - at); at = cue.atMs }
                cue(cue.kind, cue.value, cue.sound, cue.chain, cue.rowId, o, bursts, quiet)
            }
            delay(160)
            frozenRows = null; walletShown = null
            delay(900)
            clearScore()
        }
    }

    /** A new action while a show is running: jump to the end state at once (the input is never blocked). */
    fun finish() {
        job?.cancel(); job = null
        frozenRows = null; walletShown = null
        bursting.clear(); bumped.clear()
        clearScore()
    }

    private fun clearScore() {
        chips = BigInteger.ZERO; mult = 0; totalShown = null; slamAt = -1L; countFrom = -1L; labelAt = -1L
    }

    private fun cue(kind: CueKind, value: Double, sound: String?, chain: Int, rowId: Long, o: SwipeOutcome, bursts: List<Beat.Burst>, quiet: Boolean) {
        val t = now()
        when (kind) {
            CueKind.Sound -> if (soundOn() && sound != null) {
                sfx.play(sound, if (quiet) 0.35f else 1f)
                if (sound == "whoosh") { countFrom = t; countTarget = o.earned; totalShown = BigInteger.ZERO }
            }
            CueKind.Haptic -> if (!quiet) sfx.buzz(value.toLong())
            CueKind.Merge -> bumped[rowId] = t
            CueKind.HitStop -> freezeUntil = maxOf(freezeUntil, t + value.toLong())
            CueKind.Shatter -> { bursting[rowId] = t; bounds[rowId]?.let { shatter(it, value.toInt(), chain) } }
            CueKind.Shake -> shakeAmp = maxOf(shakeAmp, (value * 3.2).toFloat())
            CueKind.MultUp -> {
                mult = value.toInt()
                val step = FxPlan.step(chain)
                label = if (chain <= 1) "${step.name}!" else "${chain}연쇄 ${step.name}!"
                labelAt = t; labelHot = chain >= 6
            }
            CueKind.ChipAdd -> bursts.firstOrNull { it.chain == chain }?.let { chips = chips + it.earned }
            CueKind.Flash -> flash = 1f
            CueKind.SlowMo -> slowUntil = t + value.toLong()
            CueKind.ScreenCrack -> { if (value > crack) { crack = value.toFloat(); crackSeed++ } }
            CueKind.Wave -> {
                shakeAmp = maxOf(shakeAmp, 9f); flash = maxOf(flash, 0.45f)
                label = "과부하 웨이브 ${value.toInt()}!"; labelAt = t; labelHot = true
                bounds.values.forEach { shatter(it, 6, 6) }
            }
            CueKind.DecorCollapse -> { collapseAt = t; collapsePower = value.toFloat() }
            CueKind.Payoff -> {
                slamAt = t; totalShown = o.earned; countFrom = -1L
                frozenRows = null; walletShown = null
                flash = maxOf(flash, 0.35f); shakeAmp = maxOf(shakeAmp, 6f + value.toFloat())
            }
            CueKind.LevelUp -> { label = "레벨 업! Lv ${value.toInt()}"; labelAt = t; labelHot = false }
            CueKind.Overheat -> { label = "과열! 이번 판 상한"; labelAt = t; labelHot = true }
            else -> {}
        }
    }

    private val palette = listOf(Color(0xFFF8EFE2), Color(0xFFFFFFFF), Color(0xFFFFD66B), Color(0xFFFF8A1F), Color(0xFFFF3B30))

    private fun shatter(r: Rect, n: Int, chain: Int) {
        val power = 1f + chain * 0.08f
        repeat(n) {
            val x = r.left + rnd.nextFloat() * r.width
            val y = r.top + rnd.nextFloat() * r.height
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = (260f + rnd.nextFloat() * 760f) * power
            particles += Particle(
                x, y, cos(a) * speed, sin(a) * speed - 380f * power,
                rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 900f,
                10f + rnd.nextFloat() * 22f, palette[if (it % 3 == 0) 0 else rnd.nextInt(palette.size)],
                0f, 0.7f + rnd.nextFloat() * 0.6f, shard = it % 4 != 0,
            )
        }
        if (particles.size > 600) particles.subList(0, particles.size - 600).clear()
    }

    /** One frame. Hit-stop freezes the world (not the score layer); slow-mo scales its time. */
    fun tick() {
        val t = now()
        var dt = ((t - last).coerceIn(0, 50)) / 1000f
        last = t
        if (t < freezeUntil) dt = 0f else if (t < slowUntil) dt *= 0.3f
        if (dt > 0f) {
            val it = particles.iterator()
            while (it.hasNext()) {
                val p = it.next()
                p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 2100f * dt; p.vx *= (1f - 0.6f * dt)
                p.rot += p.vr * dt; p.life += dt
                if (p.life >= p.maxLife) it.remove()
            }
            shakeAmp *= exp(-dt * 10f)
            flash = (flash - dt * 3.2f).coerceAtLeast(0f)
            crack = (crack - dt * 0.55f).coerceAtLeast(0f)
        }
        if (shakeAmp > 0.2f && t >= freezeUntil) {
            shakeX = (rnd.nextFloat() * 2f - 1f) * shakeAmp; shakeY = (rnd.nextFloat() * 2f - 1f) * shakeAmp
        } else if (t >= freezeUntil) { shakeX = 0f; shakeY = 0f }
        collapse = if (collapseAt < 0) 0f else {
            val e = (t - collapseAt) / 1000f
            when {
                e < 0.2f -> e / 0.2f * collapsePower
                e < 1.8f -> collapsePower * exp(-(e - 0.2f) * 3.5f) * cos((e - 0.2f) * 13f)
                else -> { collapseAt = -1; 0f }
            }
        }
        if (countFrom >= 0) {
            val p = ((t - countFrom) / 780f).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            totalShown = countTarget.multiply(BigInteger.valueOf((eased * 1000).toLong())).divide(BigInteger.valueOf(1000))
        }
        frame++
    }
}
