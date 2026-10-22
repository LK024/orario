package io.github.lk024.orario.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.lk024.orario.data.Lesson
import io.github.lk024.orario.data.LessonSlot
import io.github.lk024.orario.data.ManualReminder
import io.github.lk024.orario.data.Override
import java.time.LocalDate
import kotlin.math.roundToInt

/** La lezione tenuta premuta, con la posizione della sua card sullo schermo. */
data class LessonMenuTarget(
    val date: LocalDate,
    val dayName: String,
    val slot: LessonSlot,
    val original: Lesson?,
    val override: Override?,
    val reminder: ManualReminder?,
    val active: Boolean,
    val minute: Int,
    val bounds: Rect,
) {
    val title get() = "$dayName ${date.format(DayMonthFormatter)} · ${slot.hour + 1}ª ora"
}

/**
 * Menu a comparsa sopra la lezione tenuta premuta: lo schermo si oscura, la card si
 * solleva e sotto (o sopra, se non c'è spazio) compaiono le azioni.
 */
@Composable
fun LessonMenuOverlay(
    target: LessonMenuTarget?,
    onDismiss: () -> Unit,
    onSubstitute: (LessonMenuTarget) -> Unit,
    onFree: (LessonMenuTarget) -> Unit,
    onRestore: (LessonMenuTarget) -> Unit,
    onReminder: (LessonMenuTarget) -> Unit,
) {
    // Tiene l'ultimo bersaglio mentre il menu si chiude con l'animazione
    var shown by remember { mutableStateOf(target) }
    if (target != null) shown = target
    BackHandler(enabled = target != null, onBack = onDismiss)

    AnimatedVisibility(visible = target != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
        val t = shown ?: return@AnimatedVisibility
        val density = LocalDensity.current
        val lift by transition.animateFloat(label = "lift") { if (it == EnterExitState.Visible) 1.03f else 1f }
        val elevation by transition.animateDp(label = "elevation") { if (it == EnterExitState.Visible) 8.dp else 0.dp }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Sul tema scuro serve un velo più marcato per staccare la card
                    .background(Color.Black.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.7f else 0.45f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            Box(
                Modifier
                    .offset { IntOffset(t.bounds.left.roundToInt(), t.bounds.top.roundToInt()) }
                    .size(with(density) { t.bounds.width.toDp() }, with(density) { t.bounds.height.toDp() })
                    .graphicsLayer { scaleX = lift; scaleY = lift }
            ) {
                LessonCard(
                    slot = t.slot,
                    active = t.active,
                    minute = t.minute,
                    override = t.override,
                    original = t.original,
                    hasReminder = t.reminder != null,
                    elevation = elevation,
                )
            }
            MenuPlacement(anchor = t.bounds) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .width(264.dp)
                        .animateEnterExit(
                            enter = scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(0f, 0f)) + fadeIn(),
                            exit = scaleOut(targetScale = 0.85f, transformOrigin = TransformOrigin(0f, 0f)) + fadeOut(),
                        ),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            t.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        MenuItem(
                            Icons.Rounded.SwapHoriz,
                            if (t.override?.lesson != null) "Modifica supplenza" else "Segna supplenza",
                        ) { onSubstitute(t) }
                        if (t.slot.lesson != null) {
                            if (t.reminder != null) {
                                MenuItem(Icons.Rounded.NotificationsActive, "Modifica promemoria") { onReminder(t) }
                            } else {
                                MenuItem(Icons.Rounded.NotificationAdd, "Aggiungi promemoria") { onReminder(t) }
                            }
                            MenuItem(Icons.Rounded.EventBusy, "Libera solo questo giorno") { onFree(t) }
                        }
                        if (t.override != null) {
                            MenuItem(Icons.Rounded.Restore, "Ripristina orario") { onRestore(t) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/** Mette il menu sotto la card, allineato al testo della lezione; se non c'è spazio, sopra. */
@Composable
private fun MenuPlacement(anchor: Rect, content: @Composable () -> Unit) {
    val insets = WindowInsets.systemBars
    val density = LocalDensity.current
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 16.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val top = insets.getTop(density) + margin
        val bottom = constraints.maxHeight - insets.getBottom(density) - margin
        val menu = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))

        val below = anchor.bottom.roundToInt() + gap
        val y = if (below + menu.height <= bottom) below
        else (anchor.top.roundToInt() - gap - menu.height).coerceAtLeast(top)
        val x = (anchor.left.roundToInt() + 64.dp.roundToPx())
            .coerceIn(margin, (constraints.maxWidth - menu.width - margin).coerceAtLeast(margin))

        layout(constraints.maxWidth, constraints.maxHeight) { menu.place(x, y) }
    }
}
