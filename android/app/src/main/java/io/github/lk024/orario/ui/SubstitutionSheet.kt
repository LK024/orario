package io.github.lk024.orario.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.lk024.orario.data.Day
import io.github.lk024.orario.data.Lesson

/** Valori già presenti nell'orario, proposti come scorciatoie mentre si scrive. */
data class Suggestions(val titles: List<String>, val rooms: List<String>, val teachers: List<String>) {
    companion object {
        fun from(days: List<Day>): Suggestions {
            val lessons = days.flatMap { it.lessons }.filterNotNull()
            fun distinct(values: List<String?>) = values.filterNotNull().distinct().sorted()
            return Suggestions(distinct(lessons.map { it.title }), distinct(lessons.map { it.room }), distinct(lessons.map { it.teacher }))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstitutionSheet(
    subtitle: String,
    initial: Lesson?,
    suggestions: Suggestions,
    onSave: (Lesson) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var room by rememberSaveable { mutableStateOf(initial?.room.orEmpty()) }
    var teacher by rememberSaveable { mutableStateOf(initial?.teacher.orEmpty()) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun save() {
        if (title.isBlank()) {
            showError = true
            return
        }
        onSave(Lesson(title.trim(), room.trim().ifEmpty { null }, teacher.trim().ifEmpty { null }))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // Scorrevole: con la tastiera aperta "Salva" deve restare raggiungibile
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Supplenza", style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Vale solo per questo giorno: dalla settimana dopo torna l'orario normale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Field(
                label = "Classe",
                value = title,
                onValueChange = { title = it; showError = false },
                suggestions = suggestions.titles,
                capitalization = KeyboardCapitalization.Characters,
                error = if (showError) "Inserisci la classe" else null,
                modifier = Modifier.focusRequester(focus),
            )
            Field("Aula", room, { room = it }, suggestions.rooms, KeyboardCapitalization.Sentences)
            Field("Docente (facoltativo)", teacher, { teacher = it }, suggestions.teachers, KeyboardCapitalization.Words, last = true, onDone = ::save)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Annulla") }
                Button(onClick = ::save) { Text("Salva") }
            }
        }
    }

    LaunchedEffect(Unit) { if (initial == null) focus.requestFocus() }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    capitalization: KeyboardCapitalization,
    modifier: Modifier = Modifier,
    error: String? = null,
    last: Boolean = false,
    onDone: () -> Unit = {},
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            modifier = modifier.fillMaxWidth(),
        )
        val matches = suggestions
            .filter { it.startsWith(value.trim(), ignoreCase = true) && !it.equals(value.trim(), ignoreCase = true) }
            .take(12)
        if (matches.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 4.dp)) {
                items(matches) { suggestion ->
                    SuggestionChip(onClick = { onValueChange(suggestion) }, label = { Text(suggestion) })
                }
            }
        }
    }
}
