package app.slideswitch.remote

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.slideswitch.remote.ui.SlideSwitchApp
import app.slideswitch.remote.ui.theme.SlideSwitchTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.handleDeepLink(intent?.data)
        setContent {
            val mode by viewModel.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Keep status/navigation bar icons readable when the app overrides the system theme.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SlideSwitchTheme(dark) {
                SlideSwitchApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleDeepLink(intent.data)
    }

    override fun onStart() {
        super.onStart()
        viewModel.startDiscovery()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopDiscovery()
    }
}
