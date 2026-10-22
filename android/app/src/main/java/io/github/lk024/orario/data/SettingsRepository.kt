package io.github.lk024.orario.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val seedColor: Int = DEFAULT_SEED,
    val weeklyView: Boolean = false,
) {
    companion object {
        const val DEFAULT_SEED = 0xFF006C4C.toInt()
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val seedColor = intPreferencesKey("seed_color")
        val weeklyView = booleanPreferencesKey("weekly_view")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            dynamicColor = prefs[Keys.dynamicColor] ?: true,
            seedColor = prefs[Keys.seedColor] ?: Settings.DEFAULT_SEED,
            weeklyView = prefs[Keys.weeklyView] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setSeedColor(color: Int) = context.dataStore.edit { it[Keys.seedColor] = color }
    suspend fun setWeeklyView(weekly: Boolean) = context.dataStore.edit { it[Keys.weeklyView] = weekly }
}
