package app.slideswitch.remote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

/** App preferences that aren't about a specific computer. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "System") }.getOrDefault(ThemeMode.System),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _onboarded = MutableStateFlow(prefs.getBoolean("onboarded", false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit()?.putString("theme", mode.name)?.apply()
    }

    fun finishOnboarding() {
        _onboarded.value = true
        prefs.edit()?.putBoolean("onboarded", true)?.apply()
    }
}
