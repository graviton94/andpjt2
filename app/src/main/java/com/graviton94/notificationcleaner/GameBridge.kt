package com.graviton94.notificationcleaner

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.webkit.JavascriptInterface
import com.graviton94.notificationcleaner.core.AwayClock
import com.graviton94.notificationcleaner.core.SaveCodec
import com.graviton94.notificationcleaner.core.WebSession
import com.graviton94.notificationcleaner.core.account
import com.graviton94.notificationcleaner.platform.Ads
import com.graviton94.notificationcleaner.platform.Telemetry
import org.json.JSONObject
import java.io.File

/**
 * window.Game for the game page (assets/game): every call runs the core engine through [WebSession] and answers JSON.
 * Calls arrive on the WebView's bridge thread; the lock also covers saves from the UI thread.
 */
class GameBridge(private val context: Context) {
    private val debugTransactions = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val saveFile = File(context.filesDir, "save.json")
    private val prefs = context.getSharedPreferences("clock", 0)
    private val lock = Any()
    // var: 설정의 "게임 데이터 초기화" replaces the whole session (privacy policy §데이터 삭제)
    private var session: WebSession = synchronized(lock) {
        val saved = runCatching { SaveCodec.decode(saveFile.readText()) }.getOrNull()
        WebSession(state = saved, now = now())
    }
    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun now() = System.currentTimeMillis()

    // An empty receipt is rejected by the reward ledgers. Billing/AdMob will supply real receipts in M6.
    private fun debugReceipt(kind: String): String = if (debugTransactions) "$kind-${System.nanoTime()}" else ""

    @JavascriptInterface
    fun call(method: String, argsJson: String): String = synchronized(lock) {
        val a = runCatching { JSONObject(argsJson) }.getOrDefault(JSONObject())
        val t = now()
        val previousPassDay = session.state.account.passDay
        val out = when (method) {
            "snapshot" -> session.snapshot(t)
            "sync" -> session.sync(t)
            "meta" -> session.meta(t)
            "challengeStart" -> session.challengeStart(t)
            "challengeEnd" -> session.challengeEnd(t)
            "buyCloud" -> session.buyCloud(a.optString("item"), t)
            "info" -> JSONObject().put("version", runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "").put("package", context.packageName).put("adPrivacy", ads?.privacyRequired == true).toString()
            "wipe" -> { saveFile.delete(); File(saveFile.path + ".tmp").delete(); prefs.edit().clear().apply(); session = WebSession(now = t); session.snapshot(t) }
            "swipe" -> session.swipe(a.optString("id").toLong(), t)
            "auto" -> session.auto(t)
            "hint" -> session.hint(t)
            "buy" -> session.buy(a.optString("app"), t)
            "upgrade" -> session.upgrade(a.optString("app"), t)
            "reset" -> session.reset(t)
            "panel" -> session.panel(t)
            "bm" -> session.bm(t)
            // M4: billing and ads are faked until the SDKs land (M6); ids keep every grant claim-once.
            "buyData" -> session.buyData(a.optString("pack"), debugReceipt("fake"), t)
            "buyStarter" -> session.buyStarter(debugReceipt("fake"), t)
            "buyRookie" -> session.buyRookie(debugReceipt("fake"), t)
            "claimRookie" -> session.claimRookie(t)
            "buyItem" -> session.buyItem(a.optString("item"), t)
            "watchAd" -> session.watchAd(a.optString("slot"), debugReceipt("fakead"), t)
            "buyPass" -> session.buyPass(a.optString("kind"), debugReceipt("fake"), t)
            "premium" -> session.premium(a.optString("app"), a.optString("voucher") == "1", t)
            "claimMissions" -> session.claimMissions(t)
            "attend" -> session.attend(t)
            "drill" -> session.tapDrill(t)
            "upgradeN" -> session.upgradeN(a.optString("app"), a.optString("n", "1"), t)
            "setDeck" -> session.setDeck(a.optString("apps").split(",").filter { it.isNotBlank() }, t)
            "buyBackup" -> session.buyBackup(a.optString("item"), t)
            else -> "{}"
        }
        val changesState = when (method) {
            "snapshot", "hint", "panel", "drill", "bm" -> false
            else -> true
        }
        if (changesState || previousPassDay != session.state.account.passDay) save()
        out
    }

    /** Set by MainActivity: the rewarded-ad host and a way to call back into the page. */
    var ads: Ads? = null
    var js: ((String) -> Unit)? = null

    /** Page asks for a rewarded ad; the answer comes back as window.onAdResult(id, earned, reason). */
    @JavascriptInterface
    fun showAd(id: String) {
        val q = JSONObject.quote(id)
        val a = ads ?: return run { js?.invoke("window.onAdResult && window.onAdResult($q, false, 'none')") }
        a.show { earned, reason -> js?.invoke("window.onAdResult && window.onAdResult($q, $earned, ${JSONObject.quote(reason)})") }
    }

    /** Settings › 광고 개인정보 설정: the UMP privacy options form. */
    @JavascriptInterface
    fun adPrivacy() { ads?.showPrivacy() }

    /** Analytics event from the page (no-op without Firebase). */
    @JavascriptInterface
    fun log(name: String, paramsJson: String) = Telemetry.event(name, paramsJson)

    @JavascriptInterface
    fun buzz(ms: Int) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms.toLong().coerceIn(5, 80), VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    /** On return: pay the time away (clock-tamper safe) and claim the daily attendance; null when nothing to show. */
    fun resume(): String? = synchronized(lock) {
        val lastWall = prefs.getLong("wall", 0L)
        if (lastWall <= 0) { save(); return null }
        val away = AwayClock.awayMs(lastWall, now(), prefs.getLong("elapsed", 0L), SystemClock.elapsedRealtime(), sameBoot = prefs.getInt("boot", -2) == bootCount())
        if (away < 60_000) return null
        val out = session.offline(away, now())
        save()
        out
    }

    fun save() = synchronized(lock) {
        val tmp = File(saveFile.path + ".tmp")
        tmp.writeText(session.save())
        tmp.renameTo(saveFile)
        prefs.edit().putLong("wall", now()).putLong("elapsed", SystemClock.elapsedRealtime()).putInt("boot", bootCount()).apply()
    }

    private fun bootCount() = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
}
