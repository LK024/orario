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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.lk024.orario.data.Settings
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
    onRefresh: () -> Unit,
    onOpenSheet: () -> Unit,
) {
    val listColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
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
