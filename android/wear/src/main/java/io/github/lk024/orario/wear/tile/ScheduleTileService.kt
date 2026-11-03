package io.github.lk024.orario.wear.tile

import android.content.ComponentName
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import io.github.lk024.orario.wear.MainActivity
import io.github.lk024.orario.wear.R
import io.github.lk024.orario.wear.WearApplication
import io.github.lk024.orario.wear.tileChanges
import io.github.lk024.orario.wear.tileState
import io.github.lk024.orario.wear.toMillis
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Tile della giornata: le lezioni come un carousel verticale (quella in corso grande, la prossima
 * media, le altre a pillola). Dopo l'ultima lezione "Sei libero",
 * poi il giorno dopo; nel weekend "Buon weekend" fino alla sera prima del rientro.
 */
class ScheduleTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        SuspendToFutureAdapter.launchFuture {
            val app = application as WearApplication
            app.load()
            // Se i dati sono vecchi si aggiornano in background e il Tile si ridisegna da solo
            app.appScope.launch { app.refresh(maxAgeMinutes = 30) }

            val schedule = app.scheduleRepository.schedule.value
            val overrides = app.overridesRepository.overrides.value
            val open = launchAction(ComponentName(this@ScheduleTileService, MainActivity::class.java))
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

            // Una schermata già pronta per ogni cambio: il Tile passa da sola all'ora successiva
            val moments = listOf(now) + (schedule?.tileChanges(now, overrides) ?: emptyList())
            val timeline = TimelineBuilders.Timeline.Builder()
            moments.forEachIndexed { index, start ->
                val validity = TimelineBuilders.TimeInterval.Builder().setStartMillis(start.toMillis())
                moments.getOrNull(index + 1)?.let { validity.setEndMillis(it.toMillis()) }
                val root = materialScope(this@ScheduleTileService, requestParams.deviceConfiguration) {
                    val state = schedule?.tileState(start, overrides)
                    TileLayout(this, requestParams.deviceConfiguration.screenHeightDp, open).render(state, start)
                }
                timeline.addTimelineEntry(
                    TimelineBuilders.TimelineEntry.Builder()
                        .setValidity(validity.build())
                        .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                        .build()
                )
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline.build())
                .setFreshnessIntervalMillis(30 * 60_000L)
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        SuspendToFutureAdapter.launchFuture {
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    TileLayout.PERSON_ICON,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(R.drawable.ic_person).build()
                        )
                        .build(),
                )
                .build()
        }

    private companion object {
        const val RESOURCES_VERSION = "2"
    }
}
