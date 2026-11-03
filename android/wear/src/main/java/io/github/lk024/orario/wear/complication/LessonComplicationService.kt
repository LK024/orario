package io.github.lk024.orario.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import io.github.lk024.orario.data.Lesson
import io.github.lk024.orario.data.LessonSlot
import io.github.lk024.orario.data.formatMinutes
import io.github.lk024.orario.wear.MainActivity
import io.github.lk024.orario.wear.R
import io.github.lk024.orario.wear.Status
import io.github.lk024.orario.wear.WearApplication
import io.github.lk024.orario.wear.changesToday
import io.github.lk024.orario.wear.statusAt
import io.github.lk024.orario.wear.weekdayLabel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Complicazione: la lezione in corso o la prossima ("3B · fino 10:20", "5D alle 12:10"). */
class LessonComplicationService : SuspendingTimelineComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        data(type, Status.InLesson(LessonSlot(0, Lesson("3B", "Lab 2"), 8 * 60 + 20, 9 * 60 + 20), Lesson("3B", "Lab 2")))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val app = application as WearApplication
        app.load()
        val schedule = app.scheduleRepository.schedule.value ?: return null
        val overrides = app.overridesRepository.overrides.value
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

        // Un valore già pronto per ogni cambio d'ora di oggi; dopo l'ultima, il prossimo giorno
        val moments = listOf(now) + schedule.changesToday(now, overrides)
        val entries = moments.zipWithNext().mapNotNull { (start, end) ->
            data(request.complicationType, schedule.statusAt(start, overrides))?.let {
                TimelineEntry(TimeInterval(start.instant(), end.instant()), it)
            }
        }
        val after = data(request.complicationType, schedule.statusAt(moments.last(), overrides)) ?: return null
        return ComplicationDataTimeline(after, entries)
    }

    private fun data(type: ComplicationType, status: Status): ComplicationData? {
        val (short, long, title) = when (status) {
            is Status.InLesson -> Triple(
                status.lesson.title,
                listOfNotNull(status.lesson.title, status.lesson.room).joinToString(" · "),
                "fino ${formatMinutes(status.slot.end)}",
            )
            is Status.InPause -> Triple(
                status.next.lesson!!.title,
                "${status.pause.kind.label}, poi ${status.next.lesson!!.title}",
                formatMinutes(status.next.start),
            )
            is Status.Next -> Triple(
                status.lesson.title,
                listOfNotNull(status.lesson.title, status.lesson.room).joinToString(" · "),
                if (status.today) formatMinutes(status.slot.start)
                else "${status.date.weekdayLabel().take(3)} ${formatMinutes(status.slot.start)}",
            )
            Status.Nothing -> Triple("–", "Nessuna lezione", "")
        }
        val description = PlainComplicationText.Builder("$long $title").build()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_complication)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder(short).build(), description)
                .setTitle(PlainComplicationText.Builder(title).build())
                .setTapAction(tap)
                .build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(PlainComplicationText.Builder(long).build(), description)
                .setTitle(PlainComplicationText.Builder(title).build())
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build()
            else -> null
        }
    }

    private fun LocalDateTime.instant() = atZone(ZoneId.systemDefault()).toInstant()
}
