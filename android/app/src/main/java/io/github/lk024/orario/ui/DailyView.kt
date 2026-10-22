package io.github.lk024.orario.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lk024.orario.data.BreakSlot
import io.github.lk024.orario.data.Day
import io.github.lk024.orario.data.Lesson
import io.github.lk024.orario.data.LessonSlot
import io.github.lk024.orario.data.ManualReminder
import io.github.lk024.orario.data.Override
import io.github.lk024.orario.data.OverrideKey
import io.github.lk024.orario.data.Slot
import io.github.lk024.orario.data.forDate
import io.github.lk024.orario.data.formatMinutes
import io.github.lk024.orario.data.timeline
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun DailyView(
    days: List<Day>,
    overrides: Map<OverrideKey, Override>,
    manualReminders: List<ManualReminder>,
    weekStart: LocalDate,
    now: LocalDateTime,
    pagerState: PagerState,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onLessonLongPress: (LessonMenuTarget) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = now.toLocalDate()
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage.coerceAtMost(days.lastIndex)) {
            days.forEachIndexed { index, day ->
                val date = day.dateIn(weekStart, index)
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.height(64.dp),
                ) {
                    DayTabLabel(day.name.take(3), date.dayOfMonth, isToday = date == today)
                }
            }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                DayPage(
                    day = days[page],
                    date = days[page].dateIn(weekStart, page),
                    overrides = overrides,
                    manualReminders = manualReminders,
                    now = now,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onLessonLongPress = onLessonLongPress,
                )
            }
        }
    }
}

@Composable
private fun DayTabLabel(name: String, dayOfMonth: Int, isToday: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$dayOfMonth",
                style = MaterialTheme.typography.titleSmall,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun DayPage(
    day: Day,
    date: LocalDate,
    overrides: Map<OverrideKey, Override>,
    manualReminders: List<ManualReminder>,
    now: LocalDateTime,
    bottomPadding: Dp,
    onLessonLongPress: (LessonMenuTarget) -> Unit,
) {
    val effective = remember(day, date, overrides) { day.forDate(date, overrides) }
    val slots = remember(effective) { effective.timeline() }
    val isToday = date == now.toLocalDate()
    val minute = now.minuteOfDay

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Spazio sotto per la barra di navigazione trasparente e il FAB
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding + 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") { DayHeader(day, date, isToday, slots) }
        items(slots, key = { it.key }) { slot ->
            val active = isToday && minute in slot
            when (slot) {
                is LessonSlot -> {
                    val original = day.lessons.getOrNull(slot.hour)
                    val override = overrides[OverrideKey(date, slot.hour)]
                    val reminder = manualReminders.reminderFor(day, date, slot.hour)
                    LessonCard(
                        slot = slot,
                        active = active,
                        minute = minute,
                        override = override,
                        original = original,
                        hasReminder = reminder != null && slot.lesson != null,
                        onLongPress = { bounds ->
                            onLessonLongPress(
                                LessonMenuTarget(date, day.name, slot, original, override, reminder, active, minute, bounds)
                            )
                        },
                    )
                }
                is BreakSlot -> BreakRow(slot, active)
            }
        }
    }
}

/** Il promemoria di quell'ora: prima quello solo per la data, poi quello settimanale. */
fun List<ManualReminder>.reminderFor(day: Day, date: LocalDate, hour: Int): ManualReminder? =
    firstOrNull { it.date != null && it.matches(day, date, hour) } ?: firstOrNull { it.matches(day, date, hour) }

private val Slot.key: String
    get() = when (this) {
        is LessonSlot -> "lesson-$hour"
        is BreakSlot -> "break-$kind"
    }

@Composable
private fun DayHeader(day: Day, date: LocalDate, isToday: Boolean, slots: List<Slot>) {
    val lessons = slots.filterIsInstance<LessonSlot>().filter { it.lesson != null }
    Column(Modifier.padding(start = 4.dp, bottom = 8.dp)) {
        Text(
            text = "${day.name} ${date.format(DayMonthFormatter)}",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = when (lessons.size) {
                0 -> "Nessuna lezione"
                1 -> "1 lezione · ${formatMinutes(lessons.first().start)}–${formatMinutes(lessons.last().end)}"
                else -> "${lessons.size} lezioni · ${formatMinutes(lessons.first().start)}–${formatMinutes(lessons.last().end)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Card di un'ora. [slot] contiene la lezione effettiva di quel giorno, [original] quella
 * dell'orario fisso: se c'è una variazione ([override]) la card lo segnala.
 */
@Composable
fun LessonCard(
    slot: LessonSlot,
    active: Boolean,
    minute: Int,
    override: Override?,
    original: Lesson?,
    modifier: Modifier = Modifier,
    hasReminder: Boolean = false,
    elevation: Dp = 0.dp,
    onLongPress: ((Rect) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val lesson = slot.lesson
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // Il gesto non deve ripartire a ogni ricomposizione (es. lo scatto del minuto)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val container by animateColorAsState(
        when {
            active -> colors.primaryContainer
            lesson == null -> if (elevation > 0.dp) colors.surface else Color.Transparent
            else -> colors.surfaceContainer
        },
        label = "container",
    )
    val borderWidth by animateDpAsState(if (active) 2.dp else if (lesson == null) 1.dp else 0.dp, label = "border")
    val borderColor = if (active) colors.primary else colors.outlineVariant

    val pressModifier = if (onLongPress == null) Modifier else Modifier
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongPress?.invoke(bounds)
            })
        }
        .semantics { onLongClick(label = "Opzioni") { currentOnLongPress?.invoke(bounds); true } }

    Surface(
        modifier = modifier.then(pressModifier),
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null,
        shadowElevation = elevation,
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(
                modifier = Modifier.width(64.dp).fillMaxHeight().padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(formatMinutes(slot.start), style = MaterialTheme.typography.labelLarge)
                Text(
                    formatMinutes(slot.end),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            VerticalDivider(color = if (active) colors.primary.copy(alpha = 0.4f) else colors.outlineVariant)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${slot.hour + 1}ª ora",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasReminder) {
                        Icon(
                            Icons.Rounded.NotificationsActive,
                            contentDescription = "Promemoria impostato",
                            tint = colors.primary,
                            modifier = Modifier.padding(end = 6.dp).size(16.dp),
                        )
                    }
                    if (override != null) {
                        if (override.lesson != null) {
                            InfoChip(Icons.Rounded.SwapHoriz, "Supplenza", colors.tertiary, colors.onTertiary)
                        } else {
                            InfoChip(Icons.Rounded.EventBusy, "Libera oggi", colors.tertiary, colors.onTertiary)
                        }
                    }
                }
                if (lesson == null) {
                    Text("Libera", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                    if (override != null && original != null) {
                        Text(
                            listOfNotNull(original.title, original.room).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                } else {
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                    if (lesson.room != null || lesson.teacher != null) {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            lesson.room?.let { InfoChip(Icons.Outlined.MeetingRoom, it, colors.tertiaryContainer, colors.onTertiaryContainer) }
                            lesson.teacher?.let { InfoChip(Icons.Outlined.Person, it, colors.secondaryContainer, colors.onSecondaryContainer) }
                        }
                    }
                }
                if (active) {
                    val progress = (minute - slot.start).toFloat() / (slot.end - slot.start).coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Text(
                        "Termina alle ${formatMinutes(slot.end)} · mancano ${slot.end - minute} min",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BreakRow(slot: BreakSlot, active: Boolean) {
    val color by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "break",
    )
    val thickness = if (active) 2.dp else 1.dp
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), thickness = thickness, color = color)
        Text(
            text = "${slot.kind.label} · ${formatMinutes(slot.start)}–${formatMinutes(slot.end)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), thickness = thickness, color = color)
    }
}
