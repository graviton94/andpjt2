package com.graviton94.notificationcleaner

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.graviton94.notificationcleaner.core.Actor
import com.graviton94.notificationcleaner.core.AwayClock
import com.graviton94.notificationcleaner.core.Catalog
import com.graviton94.notificationcleaner.core.Engine
import com.graviton94.notificationcleaner.core.GameState
import com.graviton94.notificationcleaner.core.Meta
import com.graviton94.notificationcleaner.core.SaveCodec
import com.graviton94.notificationcleaner.core.Shop
import com.graviton94.notificationcleaner.core.SwipeOutcome
import com.graviton94.notificationcleaner.fx.FxDirector
import com.graviton94.notificationcleaner.fx.Sfx
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigInteger

/**
 * The only place that changes the game: every action goes through the core engine, then is saved
 * (one JSON file, written atomically) and its FX plan is played. The UI only reads [state].
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    val engine = Engine()
    private val meta = Meta(engine)
    val shop = Shop(engine)
    private val sfx = Sfx(app)
    /** Plays every swipe's FX plan; the screen draws from it. */
    val fx = FxDirector(sfx, viewModelScope) { soundOn }
    private val saveFile = File(app.filesDir, "save.json")
    private val prefs = app.getSharedPreferences("clock", 0)

    var state by mutableStateOf(load())
        private set
    /** The last swipe the board should celebrate; [fxSeq] ticks so the UI can react once per swipe. */
    var lastOutcome by mutableStateOf<SwipeOutcome?>(null)
        private set
    var fxSeq by mutableIntStateOf(0)
        private set
    /** P paid on return, shown once. */
    var offlinePaid by mutableStateOf<BigInteger?>(null)
    var shopOpen by mutableStateOf(false)
    var soundOn by mutableStateOf(true)

    private var loop: Job? = null
    private var lastAuto = 0L

    private fun now() = System.currentTimeMillis()

    private fun load(): GameState =
        runCatching { SaveCodec.decode(saveFile.readText()) }.getOrNull() ?: engine.newGame(now(), now())

    private fun save() {
        val tmp = File(saveFile.path + ".tmp")
        tmp.writeText(SaveCodec.encode(state))
        tmp.renameTo(saveFile)
        prefs.edit().putLong("wall", now()).putLong("elapsed", SystemClock.elapsedRealtime()).putInt("boot", bootCount()).apply()
    }

    private fun bootCount() = runCatching { Settings.Global.getInt(getApplication<Application>().contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    fun resume() {
        val lastWall = prefs.getLong("wall", 0L)
        if (lastWall > 0) {
            val away = AwayClock.awayMs(lastWall, now(), prefs.getLong("elapsed", 0L), SystemClock.elapsedRealtime(), sameBoot = prefs.getInt("boot", -2) == bootCount())
            if (away >= 60_000) {
                val (next, paid) = engine.settleOffline(state, away, now())
                state = next
                if (paid.signum() > 0) { offlinePaid = paid; sfx.play("ui.offline") }
            }
        }
        state = shop.claimAttendance(state, now())
        save()
        loop?.cancel()
        loop = viewModelScope.launch {
            while (true) {
                delay(200)
                tickOnce()
            }
        }
    }

    fun pause() {
        loop?.cancel()
        save()
    }

    private fun tickOnce() {
        val t = now()
        state = engine.tick(state, t)
        if (t < state.autoPausedUntil || t - lastAuto < engine.autoIntervalMs(state, t)) return
        lastAuto = t
        if (fx.busy) return // let the show finish before the auto-cleaner moves the board
        val target = engine.autoTarget(state, t) ?: return
        val prev = state
        engine.swipe(state, target, t, Actor.Auto)?.let { apply(it, t, auto = true, prev) }
    }

    fun swipe(rowId: Long) {
        val t = now()
        val prev = state
        val o = engine.swipe(state, rowId, t) ?: run { sfx.play("ui.error"); return }
        apply(o, t, auto = false, prev)
    }

    private fun apply(o: SwipeOutcome, t: Long, auto: Boolean, prev: GameState) {
        state = meta.record(o.state, o, t)
        if (!auto || o.chain > 0) { lastOutcome = o; fxSeq++ }
        fx.run(o, prev.rows, prev.wallet, quiet = auto)
        save()
    }

    // Shop (M1 minimum: buy the next apps, upgrade the deck, factory reset) -----------------------

    fun buyableApps() = Catalog.apps.filter { it.id !in state.appLevels && engine.unlocked(state, it.id) }.sortedBy { engine.appPrice(state, it.id) }.take(4)

    fun buy(appId: String) { val n = engine.buy(state, appId); if (n !== state) { state = n; sfx.play("ui.buy"); save() } else sfx.play("ui.error") }

    fun upgrade(appId: String) { val n = engine.upgrade(state, appId); if (n !== state) { state = meta.upgraded(n, 1, now()); sfx.play("ui.buy"); save() } else sfx.play("ui.error") }

    fun factoryReset() { meta.reset(state, now())?.let { state = it; sfx.play("ui.levelup"); save() } }

    override fun onCleared() { sfx.release(); super.onCleared() }
}
