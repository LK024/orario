package io.github.lk024.orario.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    val liveDay: Boolean = true,
) {
    val reminderSettings get() = ReminderSettings(blockReminders, blockLead, lunchReminders, lunchLead, pauseFrom, pauseTo)

    companion object {
        // Lo stesso rosso dell'icona
        const val DEFAULT_SEED = 0xFFD93025.toInt()
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context, private val api: SubstitutionsApi) {

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
        /** La pausa cambiata qui e non ancora arrivata nel foglio. */
        val pausePending = booleanPreferencesKey("pause_pending")
        val liveDay = booleanPreferencesKey("live_day")
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
            liveDay = prefs[Keys.liveDay] ?: true,
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
    suspend fun setLiveDay(enabled: Boolean) = context.dataStore.edit { it[Keys.liveDay] = enabled }
    /** Imposta (o toglie, con null) la pausa qui; la manda al foglio [syncPause]. */
    suspend fun setPause(from: LocalDate?, to: LocalDate?) = context.dataStore.edit {
        if (from != null && to != null) {
            it[Keys.pauseFrom] = from.toString()
            it[Keys.pauseTo] = to.toString()
        } else {
            it.remove(Keys.pauseFrom)
            it.remove(Keys.pauseTo)
        }
        it[Keys.pausePending] = true
    }

    /**
     * Allinea la pausa con il foglio, da cui la leggono anche il Mac e chi la imposta da lì: come per le
     * supplenze, quella cambiata qui e non ancora inviata vince.
     */
    suspend fun syncPause(): SyncResult = withContext(Dispatchers.IO) {
        if (!api.enabled) return@withContext SyncResult.Unchanged
        val prefs = context.dataStore.data.first()
        val sentFrom = prefs[Keys.pauseFrom]
        val sentTo = prefs[Keys.pauseTo]
        val remote = runCatching {
            if (prefs[Keys.pausePending] == true) api.pushPause(sentFrom?.let(LocalDate::parse), sentTo?.let(LocalDate::parse))
            else api.fetchPause()
        }.onFailure { Log.w(TAG, "Sync pausa fallito", it) }.getOrNull() ?: return@withContext SyncResult.Failed
        // Script non ancora aggiornato: resta tutto com'è, e la modifica resta da inviare
        if (remote == RemotePause.Unsupported) return@withContext SyncResult.Unchanged
        val period = remote as? RemotePause.Period
        var changed = false
        context.dataStore.edit {
            // Cambiata di nuovo durante la richiesta: la invia il prossimo aggiornamento
            if (it[Keys.pauseFrom] != sentFrom || it[Keys.pauseTo] != sentTo) return@edit
            changed = period?.from?.toString() != sentFrom || period?.to?.toString() != sentTo
            if (period != null) {
                it[Keys.pauseFrom] = period.from.toString()
                it[Keys.pauseTo] = period.to.toString()
            } else {
                it.remove(Keys.pauseFrom)
                it.remove(Keys.pauseTo)
            }
            it.remove(Keys.pausePending)
        }
        if (changed) SyncResult.Updated else SyncResult.Unchanged
    }

    private companion object {
        const val TAG = "Settings"
    }
}
