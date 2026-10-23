package io.github.lk024.orario.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lk024.orario.data.ColorStyle
import io.github.lk024.orario.data.Day
import io.github.lk024.orario.data.ManualReminder
import io.github.lk024.orario.data.Settings
import io.github.lk024.orario.data.normalized
import io.github.lk024.orario.data.ThemeMode
import io.github.lk024.orario.ui.theme.SeedColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    lastSync: Long?,
    today: LocalDate,
    refreshing: Boolean,
    onDismiss: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onSeedColorChange: (Int) -> Unit,
    onColorStyleChange: (ColorStyle) -> Unit,
    onRefresh: () -> Unit,
    onOpenSheet: () -> Unit,
    days: List<Day>,
    manualReminders: List<ManualReminder>,
    notificationsAllowed: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onBlockRemindersChange: (Boolean, Int) -> Unit,
    onLunchRemindersChange: (Boolean, Int) -> Unit,
    onPauseChange: (LocalDate?, LocalDate?) -> Unit,
    onRemoveManualReminder: (ManualReminder) -> Unit,
) {
    var showPause by rememberSaveable { mutableStateOf(false) }
    val listColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SectionTitle("Aspetto")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                val modes = listOf(ThemeMode.System to "Sistema", ThemeMode.Light to "Chiaro", ThemeMode.Dark to "Scuro")
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(label) }
                }
            }
            ListItem(
                headlineContent = { Text("Colori dello sfondo") },
                supportingContent = { Text("Usa la palette di sistema (Material You)") },
                trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = null) },
                colors = listColors,
                modifier = Modifier.toggleable(
                    value = settings.dynamicColor,
                    role = Role.Switch,
                    onValueChange = onDynamicColorChange,
                ).padding(horizontal = 8.dp),
            )
            AnimatedVisibility(visible = !settings.dynamicColor) {
                Column {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                        val styles = listOf(ColorStyle.Standard to "Standard", ColorStyle.Vibrant to "Vibrante")
                        styles.forEachIndexed { index, (style, label) ->
                            SegmentedButton(
                                selected = settings.colorStyle == style,
                                onClick = { onColorStyleChange(style) },
                                shape = SegmentedButtonDefaults.itemShape(index, styles.size),
                            ) { Text(label) }
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SeedColors.forEach { color ->
                            ColorSwatch(color, selected = color == settings.seedColor) { onSeedColorChange(color) }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SectionTitle("Promemoria")
            if (!notificationsAllowed && (settings.blockReminders || settings.lunchReminders || manualReminders.isNotEmpty())) {
                ListItem(
                    headlineContent = { Text("Notifiche disattivate") },
                    supportingContent = { Text("Senza il permesso i promemoria non possono arrivare") },
                    leadingContent = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    trailingContent = { TextButton(onClick = onOpenNotificationSettings) { Text("Consenti") } },
                    colors = listColors,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            ReminderToggle(
                title = "Inizio delle lezioni",
                description = "Prima lezione del giorno e dopo un'ora libera",
                enabled = settings.blockReminders,
                lead = settings.blockLead,
                options = listOf(15, 20, 30, 45, 60),
                onChange = onBlockRemindersChange,
            )
            ReminderToggle(
                title = "Dopo la pausa pranzo",
                description = "Se prima di pranzo eri già a lezione",
                enabled = settings.lunchReminders,
                lead = settings.lunchLead,
                options = listOf(5, 10, 15, 20),
                onChange = onLunchRemindersChange,
            )
            val pausedUntil = settings.pauseTo?.takeIf { it >= today }
            ListItem(
                headlineContent = {
                    Text(if (pausedUntil != null) "In pausa fino al ${pausedUntil.format(DayMonthFormatter)}" else "Metti in pausa")
                },
                supportingContent = {
                    Text(
                        if (pausedUntil != null) "Dal ${settings.pauseFrom?.format(DayMonthFormatter)}: nessun promemoria, neanche quelli personali"
                        else "Per festività, ferie o permessi"
                    )
                },
                leadingContent = { Icon(Icons.Rounded.NotificationsPaused, contentDescription = null) },
                trailingContent = {
                    if (pausedUntil != null) TextButton(onClick = { onPauseChange(null, null) }) { Text("Riprendi") }
                },
                colors = listColors,
                modifier = Modifier.clickable { showPause = true }.padding(horizontal = 8.dp),
            )
            if (manualReminders.isNotEmpty()) {
                Text(
                    "Promemoria personali",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                )
                manualReminders.sortedWith(compareBy({ it.date ?: "" }, { it.dayName }, { it.hour })).forEach { reminder ->
                    val lesson = days.firstOrNull { it.name.normalized() == reminder.dayName.normalized() }?.lessons?.getOrNull(reminder.hour)
                    ListItem(
                        headlineContent = {
                            Text(listOfNotNull(lesson?.title, "${reminder.dayName.lowercase()}, ${reminder.hour + 1}ª ora").joinToString(" · "))
                        },
                        supportingContent = {
                            val repeat = reminder.date?.let { "Solo il ${LocalDate.parse(it).format(DayMonthFormatter)}" } ?: "Ogni settimana"
                            Text("$repeat · ${reminder.lead} min prima")
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveManualReminder(reminder) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Elimina promemoria")
                            }
                        },
                        colors = listColors,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            SectionTitle("Orario")
            ListItem(
                headlineContent = { Text("Aggiorna ora") },
                supportingContent = {
                    Text(lastSync?.let { "Ultimo aggiornamento ${formatLastSync(it, today)}" } ?: "Mai aggiornato")
                },
                leadingContent = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                trailingContent = { if (refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp) },
                colors = listColors,
                modifier = Modifier.clickable(enabled = !refreshing, onClick = onRefresh).padding(horizontal = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Modifica il foglio Google") },
                leadingContent = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                colors = listColors,
                modifier = Modifier.clickable(onClick = onOpenSheet).padding(horizontal = 8.dp),
            )
        }
    }

    if (showPause) {
        PauseDialog(
            today = today,
            onConfirm = { from, to -> onPauseChange(from, to); showPause = false },
            onDismiss = { showPause = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color(color), CircleShape)
            .then(
                if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape)
                else Modifier
            )
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
    }
}
