package io.github.lk024.orario.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lk024.orario.data.ManualReminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val ManualLeads = listOf(5, 10, 15, 20, 30, 45, 60)

/** Scelta di quanti minuti prima avvisare per un'ora tenuta premuta. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderSheet(
    target: LessonMenuTarget,
    onSave: (ManualReminder) -> Unit,
    onRemove: (ManualReminder) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = target.reminder
    // Su una supplenza il promemoria vale solo per quel giorno, altrimenti ogni settimana
    val date = existing?.date ?: if (target.override?.lesson != null) target.date.toString() else null
    var lead by rememberSaveable { mutableIntStateOf(existing?.lead ?: 15) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Promemoria", style = MaterialTheme.typography.headlineSmall)
                Text(target.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (date == null) "Ogni ${target.dayName.lowercase()} alla ${target.slot.hour + 1}ª ora, finché non lo elimini."
                    else "Solo ${target.date.format(DayMonthFormatter)}, per la supplenza.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text("Quanto prima?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManualLeads.forEach { minutes ->
                    FilterChip(selected = lead == minutes, onClick = { lead = minutes }, label = { Text("$minutes min") })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing != null) TextButton(onClick = { onRemove(existing) }) { Text("Elimina") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Annulla") }
                Button(onClick = { onSave(ManualReminder(target.dayName, target.slot.hour, lead, date)) }) { Text("Salva") }
            }
        }
    }
}

/** Interruttore di un promemoria automatico, con la scelta dei minuti quando è attivo. */
@Composable
fun ReminderToggle(
    title: String,
    description: String,
    enabled: Boolean,
    lead: Int,
    options: List<Int>,
    onChange: (Boolean, Int) -> Unit,
) {
    Column {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(if (enabled) "$description · $lead min prima" else description) },
            trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .toggleable(value = enabled, role = Role.Switch) { onChange(it, lead) }
                .padding(horizontal = 8.dp),
        )
        AnimatedVisibility(visible = enabled) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { minutes ->
                    FilterChip(selected = lead == minutes, onClick = { onChange(true, minutes) }, label = { Text("$minutes min") })
                }
            }
        }
    }
}

/** Calendario per scegliere il periodo di pausa dei promemoria (festività, ferie, permessi). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseDialog(today: LocalDate, onConfirm: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
        },
    )
    fun Long.toDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val from = state.selectedStartDateMillis?.toDate() ?: return@TextButton
                    onConfirm(from, state.selectedEndDateMillis?.toDate() ?: from)
                },
            ) { Text("Metti in pausa") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Pausa dei promemoria", Modifier.padding(start = 24.dp, top = 16.dp)) },
            showModeToggle = false,
            modifier = Modifier.weight(1f),
        )
    }
}
