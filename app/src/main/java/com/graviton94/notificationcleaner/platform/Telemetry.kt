package com.graviton94.notificationcleaner.platform

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.graviton94.notificationcleaner.BuildConfig
import org.json.JSONObject

/**
 * Firebase entry point (21 platform §6). Every call is a no-op when the build has no google-services.json,
 * so local and CI builds without a Firebase project behave exactly as before.
 */
object Telemetry {
    private var analytics: FirebaseAnalytics? = null

    val enabled get() = analytics != null

    fun init(context: Context) {
        if (!BuildConfig.FIREBASE || FirebaseApp.getApps(context).isEmpty()) return
        analytics = FirebaseAnalytics.getInstance(context)
    }

    /** Event names and params follow bible 18/21 (snake_case, ≤40 chars). Params arrive as flat JSON from the page. */
    fun event(name: String, paramsJson: String) {
        val a = analytics ?: return
        val p = runCatching { JSONObject(paramsJson) }.getOrNull()
        val b = Bundle()
        p?.keys()?.forEach { k ->
            when (val v = p.get(k)) {
                is Int -> b.putLong(k, v.toLong()); is Long -> b.putLong(k, v)
                is Double -> b.putDouble(k, v); is Boolean -> b.putLong(k, if (v) 1 else 0)
                else -> b.putString(k, v.toString().take(100))
            }
        }
        a.logEvent(name.take(40), b)
    }
}
