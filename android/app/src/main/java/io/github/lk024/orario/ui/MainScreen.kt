package io.github.lk024.orario.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lk024.orario.BuildConfig
import io.github.lk024.orario.data.Settings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, settings: Settings) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSync.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val overrides by viewModel.overrides.collectAsStateWithLifecycle()
    val now by rememberNow()
    val today = now.toLocalDate()
    val days = schedule?.days.orEmpty()
    val weekStart = displayedWeekStart(today)

    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pagerState = rememberPagerState(initialPage = days.todayIndex(today)) { days.size }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<LessonMenuTarget?>(null) }
    var editing by remember { mutableStateOf<LessonMenuTarget?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(message.text, message.actionLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
        }
    }
    // Primo download senza cache: porta al giorno giusto appena arriva l'orario
    LaunchedEffect(days.isEmpty()) {
        if (days.isNotEmpty()) pagerState.scrollToPage(days.todayIndex(today))
    }

    fun goToToday() {
        viewModel.setWeeklyView(false)
        scope.launch { pagerState.animateScrollToPage(days.todayIndex(today)) }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(if (settings.weeklyView) "Settimana" else "Orario") },
                actions = {
                    IconButton(onClick = ::goToToday) {
                        Icon(Icons.Rounded.Today, contentDescription = "Oggi")
                    }
                    IconButton(onClick = { viewModel.setWeeklyView(!settings.weeklyView) }) {
                        if (settings.weeklyView) {
                            Icon(Icons.Rounded.ViewDay, contentDescription = "Vista giornaliera")
                        } else {
                            Icon(Icons.Rounded.CalendarViewWeek, contentDescription = "Recap settimanale")
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Impostazioni")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!settings.weeklyView && days.isNotEmpty()) {
                FloatingActionButton(onClick = { uriHandler.openUri(BuildConfig.SHEET_EDIT_URL) }) {
                    Icon(Icons.Rounded.EditNote, contentDescription = "Modifica il foglio")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (days.isEmpty()) {
            EmptyState(syncing = syncing, onDownload = viewModel::refresh, modifier = Modifier.padding(padding))
        } else {
            AnimatedContent(
                targetState = settings.weeklyView,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "view",
            ) { weekly ->
                if (weekly) {
                    WeeklyView(
                        days = days,
                        overrides = overrides,
                        weekStart = weekStart,
                        now = now,
                        contentPadding = padding,
                        refreshing = refreshing,
                        onRefresh = viewModel::refresh,
                        onDayClick = { index ->
                            viewModel.setWeeklyView(false)
                            scope.launch { pagerState.scrollToPage(index) }
                        },
                    )
                } else {
                    DailyView(
                        days = days,
                        overrides = overrides,
                        weekStart = weekStart,
                        now = now,
                        pagerState = pagerState,
                        contentPadding = padding,
                        refreshing = refreshing,
                        onRefresh = viewModel::refresh,
                        onLessonLongPress = { menuTarget = it },
                    )
                }
            }
        }
    }

    LessonMenuOverlay(
        target = menuTarget,
        onDismiss = { menuTarget = null },
        onSubstitute = { menuTarget = null; editing = it },
        onFree = { menuTarget = null; viewModel.setOverride(it.date, it.slot.hour, null) },
        onRestore = { menuTarget = null; viewModel.clearOverride(it.date, it.slot.hour) },
    )
    }

    editing?.let { target ->
        SubstitutionSheet(
            subtitle = target.title,
            initial = target.override?.lesson,
            suggestions = remember(days) { Suggestions.from(days) },
            onSave = { lesson ->
                viewModel.setOverride(target.date, target.slot.hour, lesson)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            lastSync = lastSync,
            today = today,
            refreshing = refreshing,
            onDismiss = { showSettings = false },
            onThemeModeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
            onSeedColorChange = viewModel::setSeedColor,
            onRefresh = viewModel::refresh,
            onOpenSheet = { uriHandler.openUri(BuildConfig.SHEET_EDIT_URL) },
        )
    }
}

@Composable
private fun EmptyState(syncing: Boolean, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (syncing) {
            CircularProgressIndicator()
            Text("Scarico l'orario…", style = MaterialTheme.typography.bodyLarge)
        } else {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Nessun orario salvato", style = MaterialTheme.typography.titleLarge)
            Text(
                "Serve una connessione per scaricarlo la prima volta dal foglio Google.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onDownload) { Text("Scarica l'orario") }
        }
    }
}
