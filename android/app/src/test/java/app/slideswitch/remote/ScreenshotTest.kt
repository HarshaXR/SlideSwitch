package app.slideswitch.remote

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.slideswitch.remote.ui.SlideSwitchApp
import app.slideswitch.remote.ui.theme.SlideSwitchTheme
import org.junit.Rule
import org.junit.Test

/** Renders the main screens on the JVM: ./gradlew :app:recordPaparazziDebug (needs JDK 21+). */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    private class TestApp(base: Context) : Application() {
        init { attachBaseContext(base) }
        override fun getApplicationContext(): Context = this
        // Layoutlib throws for services it doesn't emulate (NSD, Wi-Fi); the app copes with null.
        override fun getSystemService(name: String): Any? = runCatching { super.getSystemService(name) }.getOrNull()
    }

    @Composable
    private fun Screen(dark: Boolean, onboarded: Boolean = true, tab: Int = 0) {
        val context = LocalContext.current
        val vm = remember {
            RemoteViewModel(TestApp(context)).also { if (onboarded) it.finishOnboarding() }
        }
        SlideSwitchTheme(dark) { SlideSwitchApp(vm, initialTab = tab) }
    }

    @Test fun welcome() = paparazzi.snapshot { Screen(dark = false, onboarded = false) }
    @Test fun connectLight() = paparazzi.snapshot { Screen(dark = false) }
    @Test fun connectDark() = paparazzi.snapshot { Screen(dark = true) }
    @Test fun settingsLight() = paparazzi.snapshot { Screen(dark = false, tab = 1) }
    @Test fun settingsDark() = paparazzi.snapshot { Screen(dark = true, tab = 1) }
}
