package com.hossa.mando

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private var remoteState by mutableStateOf(TvRemote.state)
    private var paired by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        paired = Prefs.host(this) != null
        TvRemote.listener = { remoteState = it }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = M.gold, background = M.bg, surface = M.card,
                    onSurface = M.text, onBackground = M.text, surfaceContainer = M.card
                )
            ) {
                if (paired) {
                    RemoteScreen(remoteState, onChangeDevice = { paired = false })
                } else {
                    SetupScreen(onPaired = {
                        paired = true
                        Prefs.host(this)?.let { TvRemote.start(this, it) }
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TvRemote.listener = { remoteState = it }
        remoteState = TvRemote.state
        if (paired) Prefs.host(this)?.let { TvRemote.start(this, it) }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) TvRemote.stop()
    }
}
