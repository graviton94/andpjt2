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
import java.util.UUID

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

internal class GameStorage(context: Context, namespace: String = "cleaner") {
    private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)

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
                level = data.getInt("level"), xp = data.getLong("xp"), points = data.number("points"), lastScore = data.optJSONObject("lastScore")?.let(::readScore),
                bestScore = if (data.optInt("version") >= 2) data.number("bestScore") else ZERO, bestCombo = data.getInt("bestCombo"),
                bestCleared = data.number("bestCleared"), pressure = data.optInt("pressure", 75).coerceIn(0, 100),
                rewardOffer = ZERO, energy = data.getInt("energy").coerceIn(0, GameEngine.ENERGY_CAP),
                nextChargeAt = data.getLong("nextCharge"), auto = data.optBoolean("auto"),
                swipes = data.optInt("swipes"), pauseUntil = data.optLong("pauseUntil"),
                eventUntil = data.optLong("eventUntil"), eventTaps = data.optInt("eventTaps"),
                lastTapAt = data.optLong("lastTapAt"), nextId = data.getLong("nextId"),
                random = data.getLong("random"),
                account = data.optJSONObject("account")?.let(::readAccount) ?: Account(legacyBest = data.number("bestScore")),
            )
            GameEngine.resume(state, data.getLong("savedAt"), now)
        }.getOrElse { GameEngine.initial(now) }
    }

    fun write(state: GameState, now: Long): Boolean {
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
        data.put("version", 2).put("account", writeAccount(state.account))
        state.lastScore?.let { data.put("lastScore", writeScore(it)) }
        return prefs.edit().putString("game", data.toString()).commit()
    }

    private fun readScore(d: JSONObject) = Score(d.number("points"), d.getLong("xp"), d.getInt("combo"),
        d.number("cleared"), d.getJSONArray("apps").objects().map {
            AppScore(NoticeApp.valueOf(it.getString("app")), it.number("score"))
        }, d.getInt("event"), d.number("personal"), d.getJSONObject("bonuses").numbers())

    private fun writeScore(s: Score) = JSONObject().put("points", s.points.toString()).put("xp", s.xp)
        .put("combo", s.combo).put("cleared", s.cleared.toString()).put("event", s.eventPercent)
        .put("personal", s.personal.toString()).put("bonuses", JSONObject(s.bonuses.mapValues { it.value.toString() }))
        .put("apps", JSONArray(s.appScores.map { JSONObject().put("app", it.app.name).put("score", it.points.toString()) }))

    private fun readAccount(d: JSONObject) = Account(
        credits = d.optLong("credits"), boosts = d.optInt("boosts"), armed = d.optBoolean("armed"),
        masteries = d.getJSONArray("masteries").names().map(NoticeApp::valueOf).toSet(),
        focus = d.optString("focus").takeIf { it.isNotEmpty() }?.let(NoticeApp::valueOf),
        trials = d.optInt("trials"), trialUses = d.optInt("trialUses"), decor = d.optInt("decor"),
        equipped = d.optBoolean("equipped"), theme = d.optBoolean("theme"), starter = d.optBoolean("starter"),
        passUntil = d.optLong("passUntil"), overloads = d.optInt("overloads"), passClaims = d.optInt("passClaims"),
        month = d.optString("month"), day = d.optString("day"), attendance = d.optInt("attendance"),
        adDay = d.optString("adDay"), receipts = d.getJSONArray("receipts").names().toSet(),
        tokens = d.optLong("tokens"), recovery = d.number("recovery"), personalBest = d.number("personalBest"),
        legacyBest = d.number("legacyBest"), mark = d.optLong("mark").takeIf { it > 0 }, won = d.optLong("won"),
        previews = d.optInt("previews"), cards = d.optInt("cards"),
        savedFocus = d.optString("savedFocus").takeIf { it.isNotEmpty() }?.let(NoticeApp::valueOf),
        lastClock = d.optLong("lastClock"), challengeOpen = d.optBoolean("challengeOpen"),
        challengeBest = d.number("challengeBest"),
        lastPurchase = d.optJSONObject("purchase")?.let {
            TestReceipt(Product.valueOf(it.getString("product")),
                it.optString("app").takeIf(String::isNotEmpty)?.let(NoticeApp::valueOf), it.getString("id"))
        },
    )

    private fun writeAccount(a: Account) = JSONObject()
        .put("credits", a.credits).put("boosts", a.boosts).put("armed", a.armed)
        .put("masteries", JSONArray(a.masteries.map { it.name })).put("focus", a.focus?.name.orEmpty())
        .put("trials", a.trials).put("trialUses", a.trialUses).put("decor", a.decor)
        .put("equipped", a.equipped).put("theme", a.theme).put("starter", a.starter)
        .put("passUntil", a.passUntil).put("overloads", a.overloads).put("passClaims", a.passClaims)
        .put("month", a.month).put("day", a.day).put("attendance", a.attendance).put("adDay", a.adDay)
        .put("receipts", JSONArray(a.receipts.toList())).put("tokens", a.tokens)
        .put("recovery", a.recovery.toString()).put("personalBest", a.personalBest.toString())
        .put("legacyBest", a.legacyBest.toString()).put("mark", a.mark ?: 0).put("won", a.won)
        .put("previews", a.previews).put("cards", a.cards).put("savedFocus", a.savedFocus?.name.orEmpty())
        .put("lastClock", a.lastClock).put("challengeOpen", a.challengeOpen).put("challengeBest", a.challengeBest.toString())
        .put("purchase", a.lastPurchase?.let { JSONObject().put("product", it.product.name)
            .put("app", it.app?.name.orEmpty()).put("id", it.id) })

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
            replace(GameEngine.resume(state, pausedAt, now))
            if (!busy) shownRows = state.rows
        }
        replace(EconomyRules.login(state, now))
        if (state.swipes > 0) message = "같은 앱이 맞닿으면 연쇄가 시작돼요."
        active = true
        applyPanel(panel)
    }

    fun pause() {
        active = false
        adJob?.cancel()
        adStarted = null
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
        val updated = GameEngine.advance(EconomyRules.login(state, now), now)
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
        if (!replace(result.state)) {
            busy = false
            return
        }
        val score = requireNotNull(result.state.lastScore)
        playback = scope.launch {
            for (frame in result.frames) {
                val hit = Strike(frame, direction, fromX, manual)
                strike = hit
                val reduced = panel.reduceMotion || !ValueAnimator.areAnimatorsEnabled()
                val hold = if (reduced) 16L else hit.freezeMs + if (hit.kind == ImpactKind.Swipe) 52L else 0L
                delay(hold)
                shownRows = if (hit.kind == ImpactKind.Overflow) state.rows else frame.rows
                displayedPoints = previous.points
                delay(if (reduced) 22L else hit.durationMs - hold)
            }
            shownRows = state.rows
            displayedPoints = state.points
            val level = if (state.level > previous.level) " · 레벨 ${state.level}" else ""
            message = if (score.combo == 0) "+${pretty(state.points - previous.points)} P$level"
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

    var pendingProduct by mutableStateOf<Pair<Product, NoticeApp?>?>(null)
        private set
    private var adStarted: Long? = null
    private var adJob: Job? = null

    fun offer(product: Product, app: NoticeApp? = null) {
        if (BuildConfig.DEBUG && !busy) pendingProduct = product to app
    }

    fun confirmPurchase(approved: Boolean) {
        val (product, app) = pendingProduct ?: return
        pendingProduct = null
        if (!approved || busy || !BuildConfig.DEBUG) return
        val receipt = UUID.randomUUID().toString()
        val next = EconomyRules.purchase(state, product, receipt, System.currentTimeMillis(), app)
        if (next != state && replace(next)) {
            message = "테스트 구매 완료"
        }
    }

    fun restore() {
        if (!BuildConfig.DEBUG || busy) return
        val receipt = state.account.lastPurchase
        if (receipt != null) {
            val (product, app, id) = receipt
            replace(EconomyRules.purchase(state, product, id, System.currentTimeMillis(), app))
        }
        message = "저장된 테스트 구매 ${state.account.receipts.size}건 확인"
    }

    fun claim() {
        if (busy || !BuildConfig.DEBUG) return
        if (adStarted != null) {
            adJob?.cancel()
            adStarted = null
            message = "테스트 광고 취소"
            return
        }
        val started = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        if (EconomyRules.ad(state, id, started) == state) return
        adStarted = started
        message = "테스트 광고 30초 · 다시 누르면 취소"
        adJob = scope.launch {
            delay(30_000)
            adStarted = null
            val next = EconomyRules.ad(state, id, System.currentTimeMillis())
            if (next != state && replace(next)) message = "증폭기 1개 지급"
        }
    }

    fun redeem(trial: Boolean) { if (!busy) replace(EconomyRules.redeem(state, trial)) }
    fun equip(app: NoticeApp, trial: Boolean) { if (!busy) replace(EconomyRules.equip(state, app, trial)) }
    fun exchange() { if (!busy) replace(EconomyRules.exchange(state)) }

    fun arm() {
        if (!busy && state.account.boosts > 0) replace(state.copy(account = state.account.copy(armed = !state.account.armed)))
    }

    fun dress() {
        if (!busy && state.account.decor > 0) replace(state.copy(account = state.account.copy(equipped = !state.account.equipped)))
    }

    fun preset(save: Boolean) {
        val a = state.account
        if (busy || now >= a.passUntil) return
        if (save) replace(state.copy(account = a.copy(savedFocus = a.focus?.takeIf { it in a.masteries })))
        else a.savedFocus?.let { equip(it, false) }
    }

    fun enterChallenge() {
        if (!busy) replace(EconomyRules.enterChallenge(state, System.currentTimeMillis()))
    }

    fun challenge(row: Long) {
        if (!busy) replace(EconomyRules.playChallenge(state, row, System.currentTimeMillis()))
    }

    fun rebirth() {
        if (!busy) replace(EconomyRules.rebirth(state, System.currentTimeMillis()))
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

    private fun replace(value: GameState): Boolean {
        if (state == value) return true
        if (!storage.write(value, System.currentTimeMillis())) {
            message = "저장에 실패했습니다. 다시 시도하세요."
            return false
        }
        state = value
        if (!busy) {
            shownRows = value.rows
            displayedPoints = value.points
        }
        return true
    }
}
