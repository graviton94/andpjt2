package com.graviton94.notificationcleaner

import android.annotation.SuppressLint
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
import androidx.webkit.WebViewAssetLoader

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
        bridge = GameBridge(applicationContext)
        val assets = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(13, 9, 7))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            isHapticFeedbackEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            addJavascriptInterface(bridge, "Game")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = assets.shouldInterceptRequest(request.url)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) Log.e("GamePage", "${m.message()} @${m.sourceId()}:${m.lineNumber()}")
                    return true
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/game/index.html")
        }
        setContentView(web)
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
