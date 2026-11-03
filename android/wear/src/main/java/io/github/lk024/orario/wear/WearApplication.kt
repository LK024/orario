package io.github.lk024.orario.wear

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.lk024.orario.data.OverridesRepository
import io.github.lk024.orario.data.ScheduleRepository
import io.github.lk024.orario.data.SubstitutionsApi
import io.github.lk024.orario.data.SyncResult
import io.github.lk024.orario.wear.complication.LessonComplicationService
import io.github.lk024.orario.wear.tile.ScheduleTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * L'orologio scarica da solo foglio e supplenze (tramite il telefono o il Wi-Fi) e li tiene in
 * cache: funziona anche col telefono lontano. Le supplenze qui si vedono soltanto.
 */
class WearApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val scheduleRepository by lazy { ScheduleRepository(filesDir, BuildConfig.SHEET_CSV_URL) }
    val overridesRepository by lazy {
        OverridesRepository(filesDir, SubstitutionsApi(BuildConfig.SYNC_URL, BuildConfig.SYNC_TOKEN))
    }
    private val refreshMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    suspend fun load() {
        scheduleRepository.load()
        overridesRepository.load()
    }

    /** Aggiorna se i dati hanno più di [maxAgeMinutes] minuti; true se qualcosa è cambiato. */
    suspend fun refresh(maxAgeMinutes: Long = 15): Boolean = refreshMutex.withLock {
        load()
        val last = scheduleRepository.lastSync.value
        if (last != null && System.currentTimeMillis() - last < maxAgeMinutes * 60_000) return false
        val schedule = scheduleRepository.sync()
        val substitutions = overridesRepository.sync()
        val changed = schedule == SyncResult.Updated || substitutions == SyncResult.Updated
        if (changed) updateSurfaces()
        changed
    }

    /** Chiede a Tile e complicazione di ridisegnarsi con i dati nuovi. */
    fun updateSurfaces() = updateSurfaces(this)

    companion object {
        fun updateSurfaces(context: Context) {
            TileService.getUpdater(context).requestUpdate(ScheduleTileService::class.java)
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, LessonComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}

/** Ogni 6 ore: aggiorna i dati e ridisegna Tile e complicazione (anche per il cambio di giorno). */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WearApplication
        app.refresh(maxAgeMinutes = 0)
        app.updateSurfaces()
        return Result.success()
    }
}
