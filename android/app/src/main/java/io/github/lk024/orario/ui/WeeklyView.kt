package io.github.lk024.orario.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lk024.orario.data.Day
import io.github.lk024.orario.data.Lesson
import io.github.lk024.orario.data.LessonSlot
import io.github.lk024.orario.data.ManualReminder
import io.github.lk024.orario.data.Override
import io.github.lk024.orario.data.OverrideKey
import io.github.lk024.orario.data.forDate
import io.github.lk024.orario.data.timeline
import java.time.LocalDate
import java.time.LocalDateTime

/** Recap settimanale: una colonna per giorno, una riga per ora. Tocca una casella per aprire il giorno. */
@Composable
fun WeeklyView(
    days: List<Day>,
    overrides: Map<OverrideKey, Override>,
    manualReminders: List<ManualReminder>,
    weekStart: LocalDate,
    now: LocalDateTime,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDayClick: (Int) -> Unit,
) {
    val today = now.toLocalDate()
    val minute = now.minuteOfDay
    // Le variazioni della settimana mostrata sono già applicate
    val effective = remember(days, overrides, weekStart) {
        days.mapIndexed { index, day -> day.forDate(day.dateIn(weekStart, index), overrides) }
    }
    val lessonSlots = remember(effective) { effective.map { day -> day.timeline().filterIsInstance<LessonSlot>() } }
    val maxHours = days.maxOf { it.config.totalHours }.coerceAtLeast(1)

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            Row(Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.width(HourLabelWidth))
                days.forEachIndexed { index, day ->
                    val isToday = day.dateIn(weekStart, index) == today
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            day.name.take(3).replaceFirstChar(Char::uppercase),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${day.dateIn(weekStart, index).dayOfMonth}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            for (hour in 0 until maxHours) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(vertical = 2.dp)) {
                    Box(Modifier.width(HourLabelWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            "${hour + 1}ª",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    effective.forEachIndexed { index, day ->
                        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp)) {
                            if (hour < day.config.totalHours) {
                                val date = day.dateIn(weekStart, index)
                                val slot = lessonSlots[index].getOrNull(hour)
                                val active = date == today && slot != null && minute in slot
                                val changed = OverrideKey(date, hour) in overrides
                                val lesson = day.lessons.getOrNull(hour)
                                val reminder = lesson != null && manualReminders.reminderFor(days[index], date, hour) != null
                                LessonCell(lesson, active, changed) { onDayClick(index) }
                                if (reminder) {
                                    Icon(
                                        Icons.Rounded.NotificationsActive,
                                        contentDescription = "Promemoria impostato",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val HourLabelWidth = 28.dp

@Composable
private fun LessonCell(lesson: Lesson?, active: Boolean, changed: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        contentColor = if (active) colors.onPrimaryContainer else colors.onSurface,
        color = when {
            active -> colors.primaryContainer
            lesson == null -> Color.Transparent
            else -> colors.surfaceContainer
        },
        border = when {
            active -> BorderStroke(2.dp, colors.primary)
            changed -> BorderStroke(2.dp, colors.tertiary)
            lesson == null -> BorderStroke(1.dp, colors.outlineVariant)
            else -> null
        },
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (lesson == null) {
                Text("–", color = colors.outline)
            } else {
                Text(lesson.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                lesson.room?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) colors.onPrimaryContainer.copy(alpha = 0.75f) else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
