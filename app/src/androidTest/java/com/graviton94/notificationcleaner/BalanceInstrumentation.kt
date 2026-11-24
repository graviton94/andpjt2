package com.graviton94.notificationcleaner

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import java.math.BigInteger.valueOf

class BalanceInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val now = 1_798_794_000_000L
            val namespace = "balance-test-${System.nanoTime()}"
            val start = GameEngine.initial(now, 19)
            var state = EconomyRules.purchase(start, Product.STARTER, "local-receipt", now, NoticeApp.Chat)
            state = EconomyRules.redeem(state, false)
            state = state.copy(account = state.account.copy(armed = true))
            val swipe = checkNotNull(GameEngine.swipe(state, 4, now)).state
            check(swipe.points == valueOf(93) && swipe.lastScore!!.personal > swipe.bestScore)
            check(swipe.account.credits == 200L && swipe.account.boosts == 0)
            check(GameStorage(targetContext, namespace).write(swipe, now))
            val restored = GameStorage(targetContext, namespace).read(now)
            check(restored.account == swipe.account && restored.lastScore == swipe.lastScore)
            check(restored.points == swipe.points && restored.rows == swipe.rows)
            check(EconomyRules.purchase(restored, Product.STARTER, "local-receipt", now, NoticeApp.Chat) == restored)
            val reborn = EconomyRules.rebirth(restored.copy(level = 70), now)
            check(GameStorage(targetContext, namespace).write(reborn, now))
            val after = GameStorage(targetContext, namespace).read(now)
            check(after.level == 45 && after.account.tokens == 1L && after.account.credits == 200L)
            val prefs = targetContext.getSharedPreferences(namespace, android.content.Context.MODE_PRIVATE)
            val previous = org.json.JSONObject(checkNotNull(prefs.getString("game", null)))
            previous.remove("account")
            previous.remove("version")
            previous.put("points", "456").put("bestScore", "9876")
            check(prefs.edit().putString("game", previous.toString()).commit())
            val migrated = GameStorage(targetContext, namespace).read(now)
            check(migrated.points == valueOf(456) && migrated.account.legacyBest == valueOf(9876))
            check(migrated.rows == after.rows && migrated.level == after.level)
            targetContext.deleteSharedPreferences(namespace)
            result.putString("stream", "\nAndroid charge → equip → swipe → restore → rebirth verified.\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
