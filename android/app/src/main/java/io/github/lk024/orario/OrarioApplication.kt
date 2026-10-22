package io.github.lk024.orario

import android.app.Application
import io.github.lk024.orario.data.OverridesRepository
import io.github.lk024.orario.data.ScheduleRepository
import io.github.lk024.orario.data.SettingsRepository
import io.github.lk024.orario.data.SyncWorker

class OrarioApplication : Application() {

    val scheduleRepository by lazy { ScheduleRepository(filesDir, BuildConfig.SHEET_CSV_URL) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val overridesRepository by lazy { OverridesRepository(filesDir) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
    }
}
