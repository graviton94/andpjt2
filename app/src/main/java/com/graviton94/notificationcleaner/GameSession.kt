package com.graviton94.notificationcleaner

import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.math.BigInteger.ZERO

internal data class PanelSettings(
    val wifi: Boolean = true,
    val bluetooth: Boolean = false,
    val quiet: Boolean = false,
    val torch: Boolean = false,
    val airplane: Boolean = false,
    val saver: Boolean = false,
    val dark: Boolean = true,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val previews: Boolean = true,
    val reduceMotion: Boolean = false,
    val automaticBrightness: Boolean = true,
    val brightness: Float = .76f,
    val network: String = "Cleanroom Wi-Fi",
    val device: String = "청소부 이어버드",
)

internal class GameStorage(context: Context) {
    private val prefs = context.getSharedPreferences("cleaner", Context.MODE_PRIVATE)

    fun read(now: Long): GameState {
        val stored = prefs.getString("game", null) ?: return GameEngine.initial(now)
        return runCatching {
            val data = JSONObject(stored)
            val owned = data.getJSONArray("owned").names().map(NoticeApp::valueOf).toSet()
            val rows = data.getJSONArray("rows").objects().map {
                Notice(it.getLong("id"), NoticeApp.valueOf(it.getString("app")), it.number("count"),
                    it.number("threshold"), it.getLong("arrived"), it.optInt("copyAfter"))
            }
            require(owned.count { !it.isEvent } >= 4 && rows.size == 7)
            val state = GameState(
                rows = rows,
                owned = owned,
                appLevels = data.getJSONObject("appLevels").longs().mapKeys { NoticeApp.valueOf(it.key) }
                    .mapValues { it.value.toInt() },
                regions = data.getJSONObject("regions").numbers().mapKeys { Region.valueOf(it.key) },
                categories = data.getJSONObject("categories").numbers().mapKeys { Category.valueOf(it.key) },
                pendingApps = data.getJSONArray("pending").names().map(NoticeApp::valueOf),
                level = data.getInt("level"), xp = data.getLong("xp"), points = data.number("points"),
                bestScore = data.number("bestScore"), bestCombo = data.getInt("bestCombo"),
                bestCleared = data.number("bestCleared"), pressure = data.optInt("pressure", 75).coerceIn(0, 100),
                rewardOffer = data.number("offer"), energy = data.getInt("energy").coerceIn(0, GameEngine.ENERGY_CAP),
                nextChargeAt = data.getLong("nextCharge"), auto = data.optBoolean("auto"),
                swipes = data.optInt("swipes"), pauseUntil = data.optLong("pauseUntil"),
                eventUntil = data.optLong("eventUntil"), eventTaps = data.optInt("eventTaps"),
                lastTapAt = data.optLong("lastTapAt"), nextId = data.getLong("nextId"),
                random = data.getLong("random"),
            )
            GameEngine.resume(state, data.getLong("savedAt"), now)
        }.getOrElse { GameEngine.initial(now) }
    }

    fun write(state: GameState, now: Long) {
        val rows = JSONArray()
        state.rows.forEach {
            rows.put(JSONObject().put("id", it.id).put("app", it.app.name).put("count", it.count.toString())
                .put("threshold", it.threshold.toString()).put("arrived", it.arrivedAt).put("copyAfter", it.copyAfter))
        }
        val data = JSONObject()
            .put("rows", rows)
            .put("owned", JSONArray(state.owned.map { it.name }))
            .put("appLevels", JSONObject(state.appLevels.mapKeys { it.key.name }))
            .put("regions", JSONObject(state.regions.mapKeys { it.key.name }.mapValues { it.value.toString() }))
            .put("categories", JSONObject(state.categories.mapKeys { it.key.name }.mapValues { it.value.toString() }))
            .put("pending", JSONArray(state.pendingApps.map { it.name }))
            .put("level", state.level).put("xp", state.xp).put("points", state.points.toString())
            .put("bestScore", state.bestScore.toString()).put("bestCombo", state.bestCombo)
            .put("bestCleared", state.bestCleared.toString()).put("pressure", state.pressure)
            .put("offer", state.rewardOffer.toString()).put("energy", state.energy).put("nextCharge", state.nextChargeAt)
            .put("auto", state.auto).put("swipes", state.swipes).put("pauseUntil", state.pauseUntil)
            .put("eventUntil", state.eventUntil).put("eventTaps", state.eventTaps).put("lastTapAt", state.lastTapAt)
            .put("nextId", state.nextId).put("random", state.random).put("savedAt", now)
        prefs.edit().putString("game", data.toString()).apply()
    }

    fun readPanel(): PanelSettings {
        val data = prefs.getString("panel", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return PanelSettings()
        return PanelSettings(
            wifi = data.optBoolean("wifi", true), bluetooth = data.optBoolean("bluetooth"),
            quiet = data.optBoolean("quiet"), torch = data.optBoolean("torch"),
            airplane = data.optBoolean("airplane"), saver = data.optBoolean("saver"),
            dark = data.optBoolean("dark", true), sound = data.optBoolean("sound", true),
            haptics = data.optBoolean("haptics", true), previews = data.optBoolean("previews", true),
            reduceMotion = data.optBoolean("reduceMotion"),
            automaticBrightness = data.optBoolean("automaticBrightness", true),
            brightness = data.optDouble("brightness", .76).toFloat().coerceIn(.1f, 1f),
            network = data.optString("network", PanelSettings().network), device = data.optString("device", PanelSettings().device),
        )
    }

    fun writePanel(panel: PanelSettings) {
        val data = JSONObject().put("wifi", panel.wifi).put("bluetooth", panel.bluetooth)
            .put("quiet", panel.quiet).put("torch", panel.torch).put("airplane", panel.airplane)
            .put("saver", panel.saver).put("dark", panel.dark).put("sound", panel.sound)
            .put("haptics", panel.haptics).put("previews", panel.previews).put("reduceMotion", panel.reduceMotion)
            .put("automaticBrightness", panel.automaticBrightness).put("brightness", panel.brightness)
            .put("network", panel.network).put("device", panel.device)
        prefs.edit().putString("panel", data.toString()).apply()
    }

    private fun JSONArray.names() = (0 until length()).map(::getString)
    private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
    private fun JSONObject.number(key: String) = optString(key).toBigIntegerOrNull() ?: ZERO
    private fun JSONObject.numbers() = keys().asSequence().associateWith { number(it) }
    private fun JSONObject.longs() = keys().asSequence().associateWith(::getLong)
}

internal data class Strike(
    val frame: ChainFrame,
    val direction: Float,
    val fromX: Float,
    val manual: Boolean,
) {
    val kind = frame.kind
    val tier = when (kind) {
        ImpactKind.Swipe -> 0
        ImpactKind.Merge -> 1
        ImpactKind.Burst -> 2
        ImpactKind.Overflow -> if (frame.combo >= 8) 4 else 3
        ImpactKind.Finish -> 4
    }
    val freezeMs = when (kind) {
        ImpactKind.Swipe -> 24L
        ImpactKind.Merge -> 48L
        ImpactKind.Burst -> 55L
        ImpactKind.Overflow -> 32L
        ImpactKind.Finish -> 85L
    }
    val durationMs = when (kind) {
        ImpactKind.Swipe -> 96L
        ImpactKind.Merge -> 168L
        ImpactKind.Burst -> 132L
        ImpactKind.Overflow -> (140 - frame.combo * 7).coerceAtLeast(68).toLong()
        ImpactKind.Finish -> 650L
    }
}

internal class GameSession(
    private val storage: GameStorage,
    private val scope: CoroutineScope,
    private val applyPanel: (PanelSettings) -> Unit,
) {
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var state by mutableStateOf(storage.read(now))
        private set
    var panel by mutableStateOf(storage.readPanel())
        private set
    var shownRows by mutableStateOf(state.rows)
        private set
    var message by mutableStateOf("문앞왔 알림을 옆으로 밀어보세요.")
        private set
    var busy by mutableStateOf(false)
        private set
    var strike by mutableStateOf<Strike?>(null)
        private set
    var displayedPoints by mutableStateOf(state.points)
        private set
    var active by mutableStateOf(false)
        private set

    private var playback: Job? = null

    private var nextAutoAt = now + GameEngine.AUTO_MS
    private var pausedAt = 0L

    fun resume() {
        now = System.currentTimeMillis()
        if (pausedAt != 0L) {
            state = GameEngine.resume(state, pausedAt, now)
            if (!busy) shownRows = state.rows
        }
        if (state.swipes > 0) message = "같은 앱이 맞닿으면 연쇄가 시작돼요."
        active = true
        applyPanel(panel)
    }

    fun pause() {
        active = false
        playback?.cancel()
        strike = null
        busy = false
        displayedPoints = state.points
        shownRows = state.rows
        pausedAt = System.currentTimeMillis()
        storage.write(state, pausedAt)
        storage.writePanel(panel)
    }

    fun tick(blocked: Boolean) {
        if (!active) return
        now = System.currentTimeMillis()
        val updated = GameEngine.advance(state, now)
        if (updated !== state) replace(updated)
        if (!blocked && !busy && state.auto && state.energy > 0 && now >= nextAutoAt && now >= state.pauseUntil) {
            GameEngine.autoTarget(state)?.let { swipe(it, manual = false) }
        }
    }

    fun swipe(rowId: Long, direction: Float = 1f, fromX: Float = 0f, manual: Boolean = true) {
        if (!active || busy) return
        val instant = System.currentTimeMillis()
        val previous = state
        val result = GameEngine.swipe(previous, rowId, instant)
        if (result == null) {
            if (state.energy == 0) message = "스와이프를 충전하고 있어요."
            return
        }
        playback?.cancel()
        busy = true
        nextAutoAt = instant + GameEngine.AUTO_MS
        replace(result.state)
        val score = requireNotNull(result.state.lastScore)
        playback = scope.launch {
            for (frame in result.frames) {
                val hit = Strike(frame, direction, fromX, manual)
                strike = hit
                val reduced = panel.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
                val hold = if (reduced) 16L else hit.freezeMs + if (hit.kind == ImpactKind.Swipe) 52L else 0L
                delay(hold)
                shownRows = frame.rows
                displayedPoints = previous.points + frame.earned
                delay(if (reduced) 22L else hit.durationMs - hold)
            }
            shownRows = state.rows
            displayedPoints = state.points
            val level = if (state.level > previous.level) " · 레벨 ${state.level}" else ""
            message = if (score.combo == 0) "+${pretty(score.points)} P$level"
                else "${score.combo}연쇄 · ${pretty(score.cleared)}개 처리$level"
            if (score.combo >= 4) {
                strike = Strike(result.frames.last().copy(kind = ImpactKind.Finish), direction, 0f, manual)
                delay(if (panel.reduceMotion || !ValueAnimator.areAnimatorsEnabled()) 20 else 128L)
            }
            busy = false
            delay(if (panel.reduceMotion) 550 else 720L)
            strike = null
        }
    }

    fun toggleAuto() {
        nextAutoAt = System.currentTimeMillis() + GameEngine.AUTO_MS
        replace(state.copy(auto = !state.auto))
    }

    fun tapEvent(): Boolean {
        val value = GameEngine.tapEvent(state, System.currentTimeMillis())
        if (value == state) return false
        replace(value)
        return true
    }

    fun claim() {
        if (busy || state.rewardOffer <= ZERO) return
        val amount = state.rewardOffer
        replace(GameEngine.claimReward(state))
        message = "테스트 보상 · +${pretty(amount)} P"
    }

    fun buy(app: NoticeApp) {
        if (!busy) replace(GameEngine.buy(state, app))
    }

    fun upgrade(app: NoticeApp) {
        if (!busy) replace(GameEngine.upgrade(state, app))
    }

    fun updatePanel(value: PanelSettings) {
        if (panel == value) return
        panel = value
        applyPanel(value)
        storage.writePanel(value)
    }

    private fun replace(value: GameState) {
        if (state == value) return
        state = value
        if (!busy) {
            shownRows = value.rows
            displayedPoints = value.points
        }
        storage.write(value, System.currentTimeMillis())
    }
}
