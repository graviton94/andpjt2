package com.graviton94.notificationcleaner.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.math.BigInteger

/**
 * The game screen is a web page (the approved FX demo, 22 §0) driven by this session: every call runs the core engine
 * and answers JSON the page can replay. The Android bridge and the desktop dev server both sit on top of it, so the
 * page behaves the same in a browser as on the phone. Persistence and clocks are the host's job.
 */
class WebSession(val engine: Engine = Engine(), state: GameState? = null, now: Long = System.currentTimeMillis()) {
    private val meta = Meta(engine)
    val shop = Shop(engine)
    var state: GameState = state ?: engine.newGame(now, now)
        private set

    /** Board, HUD and shop data. */
    fun snapshot(now: Long): String { state = engine.tick(state, now); return snap(state, now).toString() }

    /** A player swipe (or the auto-cleaner's). Null outcome = refused (no battery, row gone). */
    fun swipe(rowId: Long, now: Long, auto: Boolean = false): String {
        val prevLast = engine.tick(state, now).lastEarned
        val o = engine.swipe(state, rowId, now, if (auto) Actor.Auto else Actor.Player)
            ?: return buildJsonObject { put("ok", false); put("reason", if (engine.tick(state, now).energy <= 0) "battery" else "gone"); put("state", snap(engine.tick(state, now), now)) }.toString()
        state = meta.record(o.state, o, now)
        return outcome(o, now, prevLast).toString()
    }

    /** The auto-cleaner's move if it is due, else just the refreshed state. */
    fun auto(now: Long): String {
        state = engine.tick(state, now)
        if (now < state.autoPausedUntil) return buildJsonObject { put("ok", false); put("state", snap(state, now)) }.toString()
        val target = engine.autoTarget(state, now) ?: return buildJsonObject { put("ok", false); put("state", snap(state, now)) }.toString()
        return swipe(target, now, auto = true)
    }

    /** The row the auto-cleaner would pick: the tutorial glove points at it. */
    fun hint(now: Long): String = buildJsonObject { put("id", engine.autoTarget(engine.tick(state, now), now)) }.toString()

    fun autoIntervalMs(now: Long): Long = engine.autoIntervalMs(state, now)

    /** Away time → offline pay (the host measures the time with [AwayClock]). */
    fun offline(awayMs: Long, now: Long): String {
        val (next, paid) = engine.settleOffline(state, awayMs, now)
        state = shop.claimAttendance(next, now)
        return buildJsonObject { put("paid", paid.toString()); put("paidShort", short(paid)); put("state", snap(state, now)) }.toString()
    }

    fun buy(appId: String, now: Long): String = act(now) { engine.buy(it, appId) }
    fun upgrade(appId: String, now: Long): String = act(now) { s -> engine.upgrade(s, appId).let { n -> if (n !== s) meta.upgraded(n, 1, now) else n } }
    fun reset(now: Long): String = act(now) { meta.reset(it, now) ?: it }

    /** ×1, ×10 or "max" levels of one app; "all" = cheapest first across the deck (screens-v1 Upgrade). */
    fun upgradeN(appId: String, n: String, now: Long): String = act(now) { s ->
        val (next, done) = when (n) {
            "max" -> engine.upgradeMax(s, appId)
            "all" -> engine.upgradeAll(s)
            else -> engine.upgradeMany(s, appId, n.toIntOrNull() ?: 1)
        }
        if (done > 0) meta.upgraded(next, done, now) else s
    }

    /** 알림 훈련: one tap while the drill runs (+1% income each, up to +100%). */
    fun tapDrill(now: Long): String { state = engine.tapDrill(engine.tick(state, now), now); return buildJsonObject { put("taps", state.drillTaps) }.toString() }

    /** 클라우드 동기화 (2nd prestige) and its shop: GB survives everything. */
    fun sync(now: Long): String = act(now) { meta.sync(it, now) ?: it }

    fun buyCloud(item: String, now: Long): String = act(now) { s ->
        CloudItem.entries.firstOrNull { it.name == item }?.let { engine.buyCloud(s, it) } ?: s
    }

    fun setDeck(apps: List<String>, now: Long): String = act(now) { engine.setDeck(it, apps) ?: it }

    fun buyBackup(item: String, now: Long): String = act(now) { s ->
        BackupItem.entries.firstOrNull { it.name == item }?.let { engine.buyBackup(s, it) } ?: s
    }

    // ---- M4 monetization (bible 17, bm-screens). Billing and ads are faked until M6: every grant still goes through
    // Shop.claimOnce so the real SDK callbacks can drop in with their order / ad ids.

    /**
     * Data packs (Play product ids in docs/content/play/play-console.md). The smallest pack sets the base rate (₩22 per
     * data) and is priced to be skipped; every card shows "N배 이득" against it. The first data purchase, whichever pack,
     * pays double once (bonus capped at [firstBonusCap]), which makes ₩12,000 the best first buy; then prices are normal.
     */
    data class DataPack(val id: String, val data: Long, val won: Int)
    val dataPacks = listOf(
        DataPack("data_100", 100, 2_200), DataPack("data_540", 540, 5_900), DataPack("data_1200", 1_200, 12_000),
        DataPack("data_3700", 3_700, 33_000), DataPack("data_8600", 8_600, 69_000), DataPack("data_16800", 16_800, 119_000),
    )
    val baseWonPerData = 22.0
    val firstBonusCap = 1_200L
    /** 초심자 패스: ₩3,300 within 7 days of install → 300 now + 50 a day for 7 days + one ★ voucher. */
    val rookieWon = 3_300; val rookieNow = 300L; val rookieDaily = 50L; val rookieDays = 7
    /** 스타터 팩: ₩2,500 within 48 hours of install, once: data 500 + ★ voucher + 3 boosts + 3 overclocks. */
    val starterWon = 2_500; val starterData = 500L; val starterMs = 48 * 3_600_000L
    val starterTickets = mapOf(CreditItem.Boost to 3, CreditItem.Overclock to 3)
    /** What a ★ voucher (★1 + ★2 on a mid-tier app) would cost in data: counted in the packs' "이득". */
    val voucherData: Long get() = Catalog.apps.first { it.tier == 5 }.let { engine.rules.premiumCost(it, 1) + engine.rules.premiumCost(it, 2) }

    private fun firstBuy(s: GameState, id: String) = id !in s.account.firstBuys
    private fun bought(s: GameState, id: String) = s.withAccount { it.copy(firstBuys = it.firstBuys + id) }
    private fun installed(s: GameState, now: Long) = if (s.account.installAt >= 0) s else s.withAccount { it.copy(installDay = if (it.installDay >= 0) it.installDay else dayOf(now), installAt = now) }
    private fun rookieOpen(s: GameState, now: Long) = s.account.rookieDay < 0 && dayOf(now) - s.account.installDay < rookieDays
    private fun rookieDayNo(s: GameState, now: Long) = (dayOf(now) - s.account.rookieDay).toInt()  // 0 = purchase day
    private fun starterLeftMs(s: GameState, now: Long) = if (!firstBuy(s, "starter_pack")) 0 else (s.account.installAt + starterMs - now).coerceAtLeast(0)
    private fun firstData(s: GameState) = firstBuy(s, "data")
    fun packGets(s: GameState, p: DataPack) = if (firstData(s)) p.data + minOf(p.data, firstBonusCap) else p.data

    fun buyData(packId: String, orderId: String, now: Long): String = act(now) { s ->
        val p = dataPacks.firstOrNull { it.id == packId } ?: return@act s
        shop.claimOnce(s, orderId) { bought(shop.credit(it, packGets(it, p), bought = true), "data") } ?: s
    }

    fun buyStarter(orderId: String, now: Long): String = act(now) { s0 ->
        val s = installed(s0, now)
        if (starterLeftMs(s, now) <= 0) return@act s0
        shop.claimOnce(s, orderId) { st ->
            bought(shop.credit(st, starterData, bought = true), "starter_pack").withAccount { a ->
                a.copy(starVouchers = a.starVouchers + 1, tickets = starterTickets.entries.fold(a.tickets) { m, (k, v) -> m + (k to (m[k] ?: 0) + v) })
            }
        } ?: s0
    }

    fun buyRookie(orderId: String, now: Long): String = act(now) { s0 ->
        val s = installed(s0, now)
        if (!rookieOpen(s, now)) return@act s0
        shop.claimOnce(s, orderId) { st ->
            shop.credit(st, rookieNow, bought = true).withAccount { it.copy(rookieDay = dayOf(now), rookieClaimDay = -1, starVouchers = it.starVouchers + 1) }
        } ?: s0
    }

    /** The pass's daily 50: once a day for 7 days from the purchase day. */
    fun claimRookie(now: Long): String = act(now) { s ->
        val a = s.account
        if (a.rookieDay < 0 || rookieDayNo(s, now) !in 0 until rookieDays || a.rookieClaimDay == dayOf(now)) s
        else shop.credit(s, rookieDaily, bought = true).withAccount { it.copy(rookieClaimDay = dayOf(now)) }
    }

    fun buyItem(item: String, now: Long): String = act(now) { s ->
        val it = CreditItem.entries.firstOrNull { c -> c.name == item } ?: return@act s
        shop.useTicket(s, it, now) ?: shop.buyItem(s, it, now) ?: s
    }

    /** A rewarded placement; [adId] is the ad network's reward id (faked for now). With a month pass no ad is shown. */
    fun watchAd(slot: String, adId: String, now: Long): String = act(now) { s ->
        val a = AdSlot.entries.firstOrNull { it.name == slot } ?: return@act s
        val offer = if (a == AdSlot.BestDouble) s.lastEarned else BigInteger.ZERO
        shop.claimOnce(s, adId) { shop.watchAd(it, a, now, offer) } ?: s
    }

    fun buyPass(kind: String, orderId: String, now: Long): String = act(now) { s ->
        shop.claimOnce(s, orderId) {
            when (kind) { "month" -> shop.buyPass(it, now); "Turbo" -> shop.buySpeedPass(it, now, SpeedPass.Turbo); "Ultra" -> shop.buySpeedPass(it, now, SpeedPass.Ultra); else -> null }
        } ?: s
    }

    fun premium(appId: String, voucher: Boolean, now: Long): String = act(now) { s ->
        (if (voucher) shop.useStarVoucher(s, appId) else shop.upgradePremium(s, appId)) ?: s
    }

    /** Daily check-in on the 30-day board (also done on return from away). ok = claimed just now. */
    fun attend(now: Long): String = act(now) { shop.claimAttendance(it, now) }

    fun claimMissions(now: Long): String = act(now) { meta.claimMissions(shop, it, now) ?: it }

    fun bm(now: Long): String {
        val s = installed(engine.tick(state, now), now).also { state = it }
        val a = s.account
        val r = engine.rules
        return buildJsonObject {
            put("state", snap(s, now))
            put("data", a.credits)
            put("vouchers", a.starVouchers)
            putJsonArray("packs") { dataPacks.forEach { p -> add(buildJsonObject { put("id", p.id); put("data", p.data); put("gets", packGets(s, p)); put("won", p.won) }) } }
            put("firstData", firstData(s)); put("firstBonusCap", firstBonusCap); put("baseWon", baseWonPerData)
            put("starterLeftMs", starterLeftMs(s, now)); put("starterWon", starterWon); put("starterData", starterData)
            put("voucherData", voucherData)
            put("starterValue", starterData + voucherData + starterTickets.entries.sumOf { (k, v) -> k.price * v })
            putJsonArray("starterTickets") { starterTickets.forEach { (k, v) -> add(buildJsonObject { put("label", k.label); put("n", v) }) } }
            put("rookieOpen", rookieOpen(s, now))
            put("rookieDaysLeft", (rookieDays - (dayOf(now) - a.installDay)).toInt().coerceAtLeast(0))
            put("rookieBought", a.rookieDay >= 0)
            put("rookieDay", if (a.rookieDay >= 0) rookieDayNo(s, now) + 1 else 0)
            put("rookieCanClaim", a.rookieDay >= 0 && rookieDayNo(s, now) in 0 until rookieDays && a.rookieClaimDay != dayOf(now))
            put("rookieWon", rookieWon); put("rookieValue", rookieNow + rookieDaily * rookieDays + voucherData)
            putJsonArray("items") {
                CreditItem.entries.forEach { c -> add(buildJsonObject {
                    put("id", c.name); put("label", c.label); put("price", c.price); put("left", shop.shopLeft(s, c, now)); put("tickets", a.tickets[c] ?: 0)
                }) }
            }
            putJsonArray("ads") { AdSlot.entries.forEach { ad -> add(buildJsonObject { put("id", ad.name); put("label", ad.label); put("left", shop.adsLeft(s, ad, now)); put("daily", ad.daily) }) } }
            put("passActive", shop.passActive(s, now))
            put("passDaysLeft", ((a.passUntil - now).coerceAtLeast(0) / 86_400_000L).toInt())
            put("speedPass", a.speedPass?.name ?: "")
            put("speedDaysLeft", ((a.speedPassUntil - now).coerceAtLeast(0) / 86_400_000L).toInt())
            put("boostLeftMin", ((a.boostUntil - now).coerceAtLeast(0) / 60_000L).toInt())
            put("overclockLeftMin", ((a.overclockUntil - now).coerceAtLeast(0) / 60_000L).toInt())
            put("backupBoost", a.backupBoost)
            putJsonArray("premium") {
                s.appLevels.keys.map { Catalog.app(it) }.sortedByDescending { it.tier * 100 + it.order }.forEach { app ->
                    val stars = a.stars(app.id)
                    add(buildJsonObject {
                        put("id", app.id); put("name", app.name); put("tier", app.tier); put("stars", stars); put("max", r.premiumMaxStars)
                        put("open", shop.premiumOpen(s, app.id)); put("price", if (stars < r.premiumMaxStars) shop.premiumPrice(s, app.id) else 0)
                        put("permille", r.premiumPermille(stars)); put("voucherOk", stars < r.voucherMaxStars)
                    })
                }
            }
            // 30-day board: today's check-in day, the streak, what each day pays
            put("streak", a.streak)
            put("today", shop.attendanceDay(s, now))
            put("claimedToday", a.attendDay == dayOf(now))
            putJsonArray("board") {
                r.attendanceBoard.forEach { d -> add(buildJsonObject {
                    put("data", d.data); put("vouchers", d.starVouchers); d.title?.let { put("title", it) }
                    putJsonArray("tickets") { d.tickets.forEach { (k, v) -> add(buildJsonObject { put("id", k.name); put("label", k.label); put("n", v) }) } }
                }) }
            }
            putJsonArray("missions") {
                meta.missionsFor(now).forEach { m -> add(buildJsonObject { put("label", m.label); put("goal", m.goal); put("now", minOf(m.goal, meta.missionProgress(s, m, now))) }) }
            }
            put("missionsDone", meta.missionsDone(s, now))
            put("missionReward", r.missionCredits)
            put("titles", JsonArray(a.titles.map { JsonPrimitive(it) }))
        }.toString()
    }

    /** Everything the pull-down shade and its sheets show (screens-v1 Shade · Shop · Upgrade · Deck · Reset). */
    fun panel(now: Long): String {
        val s = engine.tick(state, now).also { state = it }
        val r = engine.rules
        val fb = s.backup.lv(BackupItem.FastBoot)
        val lo = r.levelCeil(s.level, fb)
        val hi = r.levelCeil(s.level + 1, fb)
        val progress = if (hi > lo) ((s.runEarned - lo).max(BigInteger.ZERO) * BigInteger.valueOf(1000) / (hi - lo)).toInt().coerceIn(0, 1000) else 1000
        return buildJsonObject {
            put("state", snap(s, now))
            put("levelProgress", progress)
            put("nextLevelP", big(hi))
            put("bestChain", s.bestChain)
            put("minSlots", engine.minSlots(s))
            put("maxSlots", engine.maxSlots(s))
            put("sets", engine.activeSets(s))
            putJsonArray("apps") {
                Catalog.apps.sortedWith(compareBy({ it.tier }, { it.order })).forEach { a ->
                    val owned = a.id in s.appLevels
                    val open = engine.unlocked(s, a.id)
                    if (!owned && !open && a.unlockLevel > s.level + 40 && a.minResets > s.backup.resets) return@forEach
                    add(buildJsonObject {
                        put("id", a.id); put("name", a.name); put("tier", a.tier)
                        put("region", a.region.label); put("cat", a.category.label)
                        put("owned", owned); put("active", a.id in s.active); put("open", open)
                        put("unlockLevel", a.unlockLevel); put("minResets", a.minResets)
                        if (owned) {
                            val lv = s.appLevels[a.id] ?: 1
                            put("level", lv)
                            put("price1", big(engine.upgradePrice(s, a.id)))
                            put("price10", big(engine.upgradeCost(s, a.id, 10)))
                            put("maxN", engine.upgradeMax(s, a.id).second)
                            put("nextMilestone", r.levelMilestones.firstOrNull { it > lv } ?: 0)
                            val (rs, cs) = engine.synergy(s, a.id)
                            put("synRegion", rs); put("synCat", cs)
                        } else if (open) put("price", big(engine.appPrice(s, a.id)))
                    })
                }
            }
            put("canReset", s.level >= r.resetLevel)
            put("syncLevel", r.syncLevel)
            put("canSync", s.level >= r.syncLevel)
            put("gbNow", engine.cloudGbNow(s))
            put("gb", s.backup.cloud.gb)
            put("syncs", s.backup.cloud.syncs)
            putJsonArray("cloudShop") {
                CloudItem.entries.filter { it.pack == null || it.pack in Catalog.packs }.forEach { c ->
                    add(buildJsonObject {
                        put("id", c.name); put("label", c.label); put("lv", s.backup.cloud.lv(c)); put("max", c.max)
                        put("price", r.cloudPrice(c, s.backup.cloud.lv(c))); put("open", engine.cloudOpen(s, c)); put("minSyncs", c.minSyncs)
                    })
                }
            }
            put("files", s.backup.files)
            put("resets", s.backup.resets)
            putJsonArray("backupShop") {
                BackupItem.entries.forEach { b ->
                    add(buildJsonObject {
                        put("id", b.name); put("label", b.label); put("lv", s.backup.lv(b)); put("max", engine.backupMax(s, b))
                        put("price", engine.backupPrice(s, b)); put("open", engine.backupOpen(s, b)); put("minResets", b.minResets)
                    })
                }
            }
        }.toString()
    }

    fun save(): String = SaveCodec.encode(home ?: state)

    // ---- 업적 · 칭호 · 일일 도전 (18 meta-and-ops) -------------------------------------------------------------

    /** Main game parked while a 60-second challenge board is played; saves always write the main game. */
    private var home: GameState? = null
    private var challengeEnds = 0L

    private fun achGoal(s: GameState, a: Achievement): Pair<Long, Long> {
        fun st(k: Stat) = s.account.stats[k.name] ?: 0L
        return when (a) {
            Achievement.FirstReset -> st(Stat.Resets) to 1L
            Achievement.FirstSync -> st(Stat.Syncs) to 1L
            Achievement.LastApp -> (if (Catalog.apps.maxBy { it.unlockLevel }.id in s.appLevels) 1L else 0L) to 1L
            Achievement.Chain10 -> st(Stat.BestChain) to 10L
            Achievement.Wave9 -> st(Stat.BestWave) to 9L
            Achievement.Spam1000 -> st(Stat.SpamCleared) to 1_000L
            Achievement.Crash100 -> st(Stat.CrashRestarts) to 100L
            Achievement.Pro10 -> s.pro.size.toLong() to 10L
            Achievement.Streak30 -> s.account.streak.toLong() to 30L
        }
    }

    /** Achievements with progress, held titles, 꾸미기 조각 and today's challenge keys. */
    fun meta(now: Long): String {
        val s = home ?: engine.tick(state, now).also { state = it }
        return buildJsonObject {
            putJsonArray("achievements") {
                Achievement.entries.forEach { a -> val (v, g) = achGoal(s, a)
                    add(buildJsonObject { put("id", a.name); put("title", a.title); put("pieces", a.pieces); put("got", a.title in s.account.titles); put("now", minOf(v, g)); put("goal", g) }) }
            }
            put("titles", JsonArray(s.account.titles.map { JsonPrimitive(it) }))
            put("pieces", s.account.pieces)
            put("keys", meta.challengeKeys(s, now)); put("keyCap", if (maxOf(s.backup.bestLevel, s.level) >= 52) 3 else 2)
            put("best", s.account.challengeBest); put("bestShort", short(BigInteger(s.account.challengeBest)))
            put("active", home != null); put("endsAt", challengeEnds)
            putJsonArray("deck") { meta.challengeBoard(now).active.forEach { id -> add(buildJsonObject { put("id", id); put("name", Catalog.app(id).name) }) } }
        }.toString()
    }

    /** Uses a key and swaps in today's board for 60 seconds. */
    fun challengeStart(now: Long): String {
        if (home != null) return buildJsonObject { put("ok", false); put("reason", "active") }.toString()
        val (used, board) = meta.startChallenge(engine.tick(state, now), now) ?: return buildJsonObject { put("ok", false); put("reason", "keys") }.toString()
        home = used; state = board; challengeEnds = now + 60_000
        return buildJsonObject { put("ok", true); put("endsAt", challengeEnds); put("state", snap(state, now)) }.toString()
    }

    /** Ends the run: score = P earned on the board; back to the main game. */
    fun challengeEnd(now: Long): String {
        val h = home ?: return buildJsonObject { put("ok", false) }.toString()
        val score = state.runEarned
        val best = BigInteger(h.account.challengeBest)
        state = engine.tick(meta.finishChallenge(h, state), now); home = null; challengeEnds = 0
        return buildJsonObject {
            put("ok", true); put("score", score.toString()); put("scoreShort", short(score)); put("newBest", score > best)
            put("best", state.account.challengeBest); put("bestShort", short(BigInteger(state.account.challengeBest)))
            put("piecesGot", if (score > best) 2 else 1); put("pieces", state.account.pieces); put("state", snap(state, now))
        }.toString()
    }

    private fun act(now: Long, f: (GameState) -> GameState): String {
        val before = state
        state = f(engine.tick(state, now))
        return buildJsonObject { put("ok", state !== before); put("state", snap(state, now)) }.toString()
    }

    // ---- JSON ------------------------------------------------------------------------------------------------

    private fun big(n: BigInteger): JsonElement = JsonPrimitive(n.toString())

    fun row(r: Row): JsonObject = buildJsonObject {
        put("id", r.id)
        put("kind", r.kind.name)
        put("count", r.count)
        put("threshold", r.threshold)
        put("merges", r.merges)
        r.app?.let { id ->
            val a = Catalog.app(id)
            put("app", id); put("name", a.name); put("cat", a.category.name); put("tier", a.tier)
        }
        r.event?.let { put("event", it.name); put("label", it.label) }
        r.alert?.let { put("alert", it.name); put("label", it.label) }
    }

    private fun snap(s: GameState, now: Long): JsonObject = buildJsonObject {
        put("wallet", big(s.wallet))
        put("level", s.level)
        put("gauge", s.gauge)
        put("energy", s.energy)
        put("energyCap", engine.energyCap(s))
        put("nextChargeAt", s.nextChargeAt)
        put("now", now)
        put("autoPausedUntil", s.autoPausedUntil)
        put("autoIntervalMs", engine.autoIntervalMs(s, now))
        put("swipes", s.swipes)
        put("bestSwipe", big(s.bestSwipe))
        put("deck", s.active.size)
        put("slots", engine.rules.maxSlots(s.level, s.backup))
        put("resetLevel", engine.rules.resetLevel)
        put("backupFilesNow", engine.backupFilesNow(s))
        put("files", s.backup.files)
        putJsonArray("rows") { s.rows.forEach { add(row(it)) } }
        putJsonArray("deckApps") {
            s.active.forEach { id ->
                add(buildJsonObject {
                    val a = Catalog.app(id)
                    put("id", id); put("name", a.name); put("tier", a.tier); put("level", s.appLevels[id] ?: 1)
                    put("upgradePrice", big(engine.upgradePrice(s, id)))
                })
            }
        }
        putJsonArray("shopApps") {
            Catalog.apps.filter { it.id !in s.appLevels && engine.unlocked(s, it.id) }
                .sortedBy { engine.appPrice(s, it.id) }.take(6).forEach { a ->
                    add(buildJsonObject { put("id", a.id); put("name", a.name); put("tier", a.tier); put("price", big(engine.appPrice(s, a.id))) })
                }
        }
    }

    private fun outcome(o: SwipeOutcome, now: Long, prevLast: BigInteger): JsonObject = buildJsonObject {
        put("ok", true)
        put("earned", big(o.earned))
        put("chain", o.chain)
        put("overload", o.overload)
        put("capped", big(o.capped))
        put("beats", JsonArray(o.beats.map { beat(it, prevLast) }))
        put("state", snap(state, now))
    }

    private fun beat(b: Beat, prevLast: BigInteger): JsonObject = when (b) {
        is Beat.Swiped -> buildJsonObject {
            put("t", "swiped"); put("id", b.row.id); put("earned", big(b.earned)); put("kind", b.row.kind.name)
            b.row.event?.let { put("event", it.name) }; b.row.alert?.let { put("alert", it.name) }
            // 행운 알림: the multiplier the engine rolled (0 = miss), so the page spins to the real result
            if (b.row.event == EventCard.Lotto) put("x", if (b.earned.signum() > 0 && prevLast.signum() > 0) (b.earned / prevLast).toInt() else 0)
        }
        is Beat.Merged -> buildJsonObject { put("t", "merged"); put("into", row(b.into)); put("from", b.fromId) }
        is Beat.Burst -> buildJsonObject {
            put("t", "burst"); put("row", row(b.row)); put("chain", b.chain); put("earned", big(b.earned))
            // chips × mult: the engine pays count × value × (1 + chain); the plate shows the two factors.
            put("chips", big(b.earned / BigInteger.valueOf(1L + b.chain)))
        }
        is Beat.Wave -> buildJsonObject { put("t", "wave"); put("index", b.index); put("chain", b.chain); put("earned", big(b.earned)) }
        is Beat.Spawned -> buildJsonObject { put("t", "spawned"); put("row", row(b.row)) }
        is Beat.FreeUpgrade -> buildJsonObject { put("t", "freeUpgrade"); put("app", b.app); put("name", Catalog.app(b.app).name); put("level", b.level) }
        is Beat.LevelUp -> buildJsonObject { put("t", "levelUp"); put("level", b.level) }
        is Beat.Capped -> buildJsonObject { put("t", "capped"); put("lost", big(b.lost)) }
    }
}

