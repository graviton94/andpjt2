package com.graviton94.notificationcleaner.platform

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.graviton94.notificationcleaner.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rewarded ads only (21 platform §2): UMP consent first, then one ad kept loaded.
 * The SDK only shows and confirms; the reward itself is paid by core (Shop.watchAd) after [show] reports earned.
 */
class Ads(private val activity: Activity) {
    private var ad: RewardedAd? = null
    private var loading = false
    private var fails = 0
    private val started = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    /** Consent form if the user's region needs it, then the SDK and the first load. Safe to call more than once. */
    fun start() {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                if (err != null) Log.w(TAG, "consent form: ${err.message}")
                if (consent.canRequestAds()) initOnce()
            }
        }, { err -> Log.w(TAG, "consent info: ${err.message}"); if (consent.canRequestAds()) initOnce() })
        if (consent.canRequestAds()) initOnce()  // consent from an earlier session: don't wait for the update
    }

    private fun initOnce() {
        if (!started.compareAndSet(false, true)) return
        Thread { MobileAds.initialize(activity) { main.post { load() } } }.start()
    }

    val ready get() = ad != null

    private fun load() {
        if (loading || ad != null || !started.get()) return
        loading = true
        RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(a: RewardedAd) { ad = a; loading = false; fails = 0 }
            override fun onAdFailedToLoad(e: LoadAdError) {
                ad = null; loading = false; fails++
                Log.w(TAG, "load failed ${e.code}: ${e.message}")
                // 21 §2: back off after repeated failures (30 s, growing), keep trying in the background
                main.postDelayed({ load() }, 30_000L * minOf(fails, 4))
            }
        })
    }

    /** Shows the loaded ad. [done] gets true only when the reward was earned; false if none was ready or it failed. */
    fun show(done: (earned: Boolean, reason: String) -> Unit) = main.post {
        val a = ad
        if (a == null) { load(); done(false, if (started.get()) "loading" else "consent"); return@post }
        ad = null
        var earned = false
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { done(earned, if (earned) "ok" else "closed"); load() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { done(false, "show"); load() }
        }
        a.show(activity) { earned = true }
    }

    private companion object { const val TAG = "Ads" }
}
