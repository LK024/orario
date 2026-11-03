package io.github.lk024.orario.wear

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.dynamicColorScheme
import io.github.lk024.orario.data.BreakKind
import io.github.lk024.orario.data.BreakSlot
import io.github.lk024.orario.data.LessonSlot
import io.github.lk024.orario.data.Override
import io.github.lk024.orario.data.OverrideKey
import io.github.lk024.orario.data.Schedule
import io.github.lk024.orario.data.Slot
import io.github.lk024.orario.data.dateIn
import io.github.lk024.orario.data.displayedWeekStart
import io.github.lk024.orario.data.forDate
import io.github.lk024.orario.data.formatMinutes
import io.github.lk024.orario.data.timeline
import io.github.lk024.orario.data.todayIndex
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Composable
fun OrarioWearTheme(content: @Composable () -> Unit) {
    // Colori del quadrante (Material You su Wear OS 6), altrimenti quelli predefiniti
    val colors = dynamicColorScheme(LocalContext.current) ?: ColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun WearApp(schedule: Schedule?, overrides: Map<OverrideKey, Override>) {
    val now by rememberNow()
    val today = now.toLocalDate()
    val days = schedule?.days.orEmpty()

    AppScaffold {
        if (days.isEmpty()) {
            ScreenScaffold {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (schedule == null) CircularProgressIndicator() else Text("Nessun orario", textAlign = TextAlign.Center)
                }
            }
            return@AppScaffold
        }
        val weekStart = displayedWeekStart(today)
        val pagerState = rememberPagerState(initialPage = days.todayIndex(today)) { days.size }
        // Indicatore di sistema: sulla pagina di oggi il pallino selezionato prende il colore terziario
        // (il primario spesso è bianco come i pallini normali, il terziario è l'accento colorato del tema)
        val todayPage = days.indices.firstOrNull { days[it].dateIn(weekStart, it) == today }
        HorizontalPagerScaffold(
            pagerState = pagerState,
            pageIndicator = {
                if (pagerState.currentPage == todayPage) {
                    HorizontalPageIndicator(pagerState = pagerState, selectedColor = MaterialTheme.colorScheme.tertiary)
                } else {
                    HorizontalPageIndicator(pagerState = pagerState)
                }
            },
        ) {
            HorizontalPager(state = pagerState) { page ->
                AnimatedPage(pageIndex = page, pagerState = pagerState) {
                    val date = days[page].dateIn(weekStart, page)
                    DayPage(
                        title = date.fullLabel(),
                        slots = remember(days[page], date, overrides) { days[page].forDate(date, overrides).timeline() },
                        isToday = date == today,
                        minute = now.hour * 60 + now.minute,
                        changedHours = overrides.filterKeys { it.date == date }.keys.map { it.hour }.toSet(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayPage(title: String, slots: List<Slot>, isToday: Boolean, minute: Int, changedHours: Set<Int>) {
    val listState = rememberTransformingLazyColumnState()
    // Oggi si apre già sull'ora in corso (o sulla prossima)
    LaunchedEffect(isToday) {
        if (isToday) {
            val index = slots.indexOfFirst { minute < it.end }
            if (index > 0) listState.scrollToItem(index + 1)
        }
    }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            items(slots) { slot ->
                val active = isToday && minute in slot
                when (slot) {
                    is LessonSlot -> LessonItem(slot, active, slot.hour in changedHours, minute)
                    is BreakSlot -> PauseItem(slot, active)
                }
            }
        }
    }
}

@Composable
private fun LessonItem(slot: LessonSlot, active: Boolean, changed: Boolean, minute: Int) {
    val lesson = slot.lesson
    val colors = MaterialTheme.colorScheme
    if (lesson == null) {
        // Ora libera: una riga discreta, così la giornata resta compatta sul polso
        Text(
            "${formatMinutes(slot.start)}  ·  ${if (changed) "Libera oggi" else "Libera"}",
            style = MaterialTheme.typography.labelMedium,
            color = if (active) colors.primary else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        return
    }
    val title = if (changed) "${lesson.title} · supplenza" else lesson.title
    val details = listOfNotNull(lesson.room, lesson.teacher).joinToString(" · ")
    val time = "${formatMinutes(slot.start)}–${formatMinutes(slot.end)}"
    if (!active) {
        TitleCard(
            onClick = {},
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            time = { Text(time) },
            subtitle = details.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    // Lezione in corso: colori pieni, bordo e anello con i minuti che mancano alla fine
    Card(
        onClick = {},
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        border = BorderStroke(2.dp, colors.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RemainingRing(start = slot.start, end = slot.end, minute = minute)
        }
    }
}

/** Anello come la complicazione della batteria: si riempie con l'ora e al centro ha i minuti rimasti. */
@Composable
private fun RemainingRing(start: Int, end: Int, minute: Int) {
    val colors = MaterialTheme.colorScheme
    val progress = ((minute - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            // Apertura in basso, come l'anello della batteria
            startAngle = 120f,
            endAngle = 60f,
            strokeWidth = 5.dp,
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = colors.primary,
                trackColor = colors.onPrimaryContainer.copy(alpha = 0.2f),
            ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${end - minute}", style = MaterialTheme.typography.titleSmall)
            Text("min", style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun PauseItem(slot: BreakSlot, active: Boolean) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (slot.kind == BreakKind.Lunch) Icons.Rounded.Restaurant else Icons.Rounded.LocalCafe,
            contentDescription = slot.kind.label,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "  ${formatMinutes(slot.start)}–${formatMinutes(slot.end)}",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** Ora corrente al minuto, ricalcolata quando l'app torna visibile. */
@Composable
private fun rememberNow(): State<LocalDateTime> {
    val now = remember { mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now.value = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
    }
    return now
}
