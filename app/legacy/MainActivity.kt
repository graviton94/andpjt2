package com.graviton94.notificationcleaner

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    private lateinit var session: GameSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        session = GameSession(GameStorage(applicationContext), lifecycleScope) { panel ->
            val desired = if (panel.automaticBrightness) -1f else panel.brightness
            if (window.attributes.screenBrightness != desired) {
                window.attributes = window.attributes.apply { screenBrightness = desired }
            }
        }
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val balance = (battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1).coerceAtLeast(0)
        val scale = (battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        setContent { CleanerApp(session, (balance * 100 / scale).coerceIn(0, 100)) }
    }

    override fun onResume() {
        super.onResume()
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        session.resume()
    }

    override fun onPause() {
        session.pause()
        super.onPause()
    }
}
