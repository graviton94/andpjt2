package com.graviton94.notificationcleaner

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.graviton94.notificationcleaner.platform.Ads
import com.graviton94.notificationcleaner.platform.Telemetry

/**
 * The game screen is the approved FX demo page (assets/game) running the real core engine through [GameBridge] (22 §0).
 * Served from https://appassets.androidplatform.net so fonts, icons and fetch() behave like on the web.
 */
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var bridge: GameBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Telemetry.init(applicationContext)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)
        val testQuery = if (debuggable && intent.getBooleanExtra("balance_smoke", false)) "?auto=0" else ""
        bridge = GameBridge(applicationContext)
        val ads = Ads(this)
        bridge.ads = ads
        val assets = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            setBackgroundColor(if (night) Color.rgb(13, 9, 7) else Color.rgb(238, 241, 247))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            // The page themes itself from prefers-color-scheme (system day/night); never let WebView auto-darken it.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
            isHapticFeedbackEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            addJavascriptInterface(bridge, "Game")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = assets.shouldInterceptRequest(request.url)
                // privacy policy / terms links open in the browser, the game page stays in the app
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host == "appassets.androidplatform.net") return false
                    runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)) }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) Log.e("GamePage", "${m.message()} @${m.sourceId()}:${m.lineNumber()}")
                    return true
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/game/index.html" + testQuery)
        }
        setContentView(web)
        bridge.js = { script -> web.post { web.evaluateJavascript(script, null) } }
        ads.start()
        // The board draws its own phone chrome (clock, battery = game battery), so the real bars stay out of the way.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        bridge.resume()?.let { json -> web.evaluateJavascript("window.onOffline && window.onOffline($json)", null) }
    }

    override fun onPause() {
        bridge.save()
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
