package io.github.lk024.orario.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

enum class ThemeMode { System, Light, Dark }

/** Stile della palette generata dal colore scelto: Standard (Tonal Spot) o Vibrante. */
enum class ColorStyle { Standard, Vibrant }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val seedColor: Int = DEFAULT_SEED,
    val colorStyle: ColorStyle = ColorStyle.Standard,
    val weeklyView: Boolean = false,
    val blockReminders: Boolean = false,
    val blockLead: Int = 30,
    val lunchReminders: Boolean = false,
    val lunchLead: Int = 10,
    val pauseFrom: LocalDate? = null,
    val pauseTo: LocalDate? = null,
) {
    val reminderSettings get() = ReminderSettings(blockReminders, blockLead, lunchReminders, lunchLead, pauseFrom, pauseTo)

    companion object {
        // Lo stesso rosso dell'icona
        const val DEFAULT_SEED = 0xFFD93025.toInt()
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val seedColor = intPreferencesKey("seed_color")
        val colorStyle = stringPreferencesKey("color_style")
        val weeklyView = booleanPreferencesKey("weekly_view")
        val blockReminders = booleanPreferencesKey("block_reminders")
        val blockLead = intPreferencesKey("block_lead")
        val lunchReminders = booleanPreferencesKey("lunch_reminders")
        val lunchLead = intPreferencesKey("lunch_lead")
        val pauseFrom = stringPreferencesKey("pause_from")
        val pauseTo = stringPreferencesKey("pause_to")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.System,
            dynamicColor = prefs[Keys.dynamicColor] ?: true,
            seedColor = prefs[Keys.seedColor] ?: Settings.DEFAULT_SEED,
            colorStyle = prefs[Keys.colorStyle]?.let { runCatching { ColorStyle.valueOf(it) }.getOrNull() }
                ?: ColorStyle.Standard,
            weeklyView = prefs[Keys.weeklyView] ?: false,
            blockReminders = prefs[Keys.blockReminders] ?: false,
            blockLead = prefs[Keys.blockLead] ?: 30,
            lunchReminders = prefs[Keys.lunchReminders] ?: false,
            lunchLead = prefs[Keys.lunchLead] ?: 10,
            pauseFrom = prefs[Keys.pauseFrom]?.let(LocalDate::parse),
            pauseTo = prefs[Keys.pauseTo]?.let(LocalDate::parse),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    suspend fun setSeedColor(color: Int) = context.dataStore.edit { it[Keys.seedColor] = color }
    suspend fun setColorStyle(style: ColorStyle) = context.dataStore.edit { it[Keys.colorStyle] = style.name }
    suspend fun setWeeklyView(weekly: Boolean) = context.dataStore.edit { it[Keys.weeklyView] = weekly }
    suspend fun setBlockReminders(enabled: Boolean, lead: Int) = context.dataStore.edit {
        it[Keys.blockReminders] = enabled
        it[Keys.blockLead] = lead
    }
    suspend fun setLunchReminders(enabled: Boolean, lead: Int) = context.dataStore.edit {
        it[Keys.lunchReminders] = enabled
        it[Keys.lunchLead] = lead
    }
    suspend fun setPause(from: LocalDate?, to: LocalDate?) = context.dataStore.edit {
        if (from != null && to != null) {
            it[Keys.pauseFrom] = from.toString()
            it[Keys.pauseTo] = to.toString()
        } else {
            it.remove(Keys.pauseFrom)
            it.remove(Keys.pauseTo)
        }
    }
}
