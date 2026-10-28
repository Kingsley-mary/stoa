package com.signal.reader.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SignalSettings(
    val theme: String = "auto", // "auto", "light", "dark"
    val textSizeIndex: Int = 1, // 0: 16sp, 1: 18sp, 2: 20sp, 3: 23sp
    val fontFamily: String = "sans", // "sans", "serif"
    val widgetBackground: String = "paper", // "paper", "light", "dark", "system", "article_image"
    val unreadOnlyFilter: Boolean = false,
    val homeLayout: String = "cards", // "cards", "compact"
    val backgroundRefresh: Boolean = true,
    val refreshIntervalMinutes: Long = 60,
    val wifiOnly: Boolean = false,
    val isFirstLaunchCompleted: Boolean = false,
    val recentSearches: List<String> = emptyList()
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("signal_settings", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<SignalSettings> = _settingsFlow.asStateFlow()

    private fun loadSettings(): SignalSettings {
        val recentString = prefs.getString(KEY_RECENT_SEARCHES, "") ?: ""
        val recents = if (recentString.isBlank()) emptyList() else recentString.split("|||")

        return SignalSettings(
            theme = prefs.getString(KEY_THEME, "auto") ?: "auto",
            textSizeIndex = prefs.getInt(KEY_TEXT_SIZE, 1).coerceIn(0, 3),
            fontFamily = prefs.getString(KEY_FONT_FAMILY, "sans") ?: "sans",
            widgetBackground = prefs.getString(KEY_WIDGET_BG, "paper") ?: "paper",
            unreadOnlyFilter = prefs.getBoolean(KEY_UNREAD_ONLY, false),
            homeLayout = prefs.getString(KEY_LAYOUT, "cards") ?: "cards",
            backgroundRefresh = prefs.getBoolean(KEY_BG_REFRESH, true),
            refreshIntervalMinutes = prefs.getLong(KEY_REFRESH_INTERVAL, 60),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
            isFirstLaunchCompleted = prefs.getBoolean(KEY_FIRST_LAUNCH, false),
            recentSearches = recents
        )
    }

    fun updateTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
        _settingsFlow.value = _settingsFlow.value.copy(theme = theme)
    }

    fun updateTextSizeIndex(index: Int) {
        val safe = index.coerceIn(0, 3)
        prefs.edit().putInt(KEY_TEXT_SIZE, safe).apply()
        _settingsFlow.value = _settingsFlow.value.copy(textSizeIndex = safe)
    }

    fun updateFontFamily(fontFamily: String) {
        prefs.edit().putString(KEY_FONT_FAMILY, fontFamily).apply()
        _settingsFlow.value = _settingsFlow.value.copy(fontFamily = fontFamily)
    }

    fun updateWidgetBackground(widgetBg: String) {
        prefs.edit().putString(KEY_WIDGET_BG, widgetBg).apply()
        _settingsFlow.value = _settingsFlow.value.copy(widgetBackground = widgetBg)
    }

    fun updateUnreadOnlyFilter(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UNREAD_ONLY, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(unreadOnlyFilter = enabled)
    }

    fun updateHomeLayout(layout: String) {
        prefs.edit().putString(KEY_LAYOUT, layout).apply()
        _settingsFlow.value = _settingsFlow.value.copy(homeLayout = layout)
    }

    fun updateBackgroundRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BG_REFRESH, enabled).apply()
        _settingsFlow.value = _settingsFlow.value.copy(backgroundRefresh = enabled)
    }

    fun updateRefreshInterval(minutes: Long) {
        prefs.edit().putLong(KEY_REFRESH_INTERVAL, minutes).apply()
        _settingsFlow.value = _settingsFlow.value.copy(refreshIntervalMinutes = minutes)
    }

    fun updateWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        _settingsFlow.value = _settingsFlow.value.copy(wifiOnly = wifiOnly)
    }

    fun setFirstLaunchCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, completed).apply()
        _settingsFlow.value = _settingsFlow.value.copy(isFirstLaunchCompleted = completed)
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _settingsFlow.value.recentSearches.filterNot { it.equals(trimmed, ignoreCase = true) }
        val updated = (listOf(trimmed) + current).take(6)
        prefs.edit().putString(KEY_RECENT_SEARCHES, updated.joinToString("|||")).apply()
        _settingsFlow.value = _settingsFlow.value.copy(recentSearches = updated)
    }

    fun removeRecentSearch(query: String) {
        val updated = _settingsFlow.value.recentSearches.filterNot { it.equals(query, ignoreCase = true) }
        prefs.edit().putString(KEY_RECENT_SEARCHES, updated.joinToString("|||")).apply()
        _settingsFlow.value = _settingsFlow.value.copy(recentSearches = updated)
    }

    fun clearRecentSearches() {
        prefs.edit().putString(KEY_RECENT_SEARCHES, "").apply()
        _settingsFlow.value = _settingsFlow.value.copy(recentSearches = emptyList())
    }

    companion object {
        private const val KEY_THEME = "theme"
        private const val KEY_TEXT_SIZE = "text_size_index"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_WIDGET_BG = "widget_background"
        private const val KEY_UNREAD_ONLY = "unread_only_filter"
        private const val KEY_LAYOUT = "home_layout"
        private const val KEY_BG_REFRESH = "background_refresh"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval_minutes"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_FIRST_LAUNCH = "first_launch_completed"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
    }
}
