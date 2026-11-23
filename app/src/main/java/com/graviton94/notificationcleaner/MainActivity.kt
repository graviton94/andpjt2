package com.graviton94.notificationcleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.graviton94.notificationcleaner.ui.GameScreen

/** Greybox (22 greybox-plan, M1): the real core engine behind a plain board. */
class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GameScreen(vm) }
    }

    override fun onResume() {
        super.onResume()
        vm.resume()
    }

    override fun onPause() {
        vm.pause()
        super.onPause()
    }
}
