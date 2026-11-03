package io.github.lk024.orario.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.protolayout.types.layoutString
import io.github.lk024.orario.data.BreakSlot
import io.github.lk024.orario.data.LessonSlot
import io.github.lk024.orario.data.Slot
import io.github.lk024.orario.data.formatMinutes
import io.github.lk024.orario.wear.DayAgenda
import io.github.lk024.orario.wear.TileState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Disegna una schermata del Tile per un certo istante. */
internal class TileLayout(
    private val scope: MaterialScope,
    private val screenHeightDp: Int,
    private val open: ActionBuilders.Action,
) {
    private val colors = scope.colorScheme
    private val contentHeight = screenHeightDp * (1 - 2 * VERTICAL_INSET)

    fun render(state: TileState?, at: LocalDateTime): LayoutElement {
        val content = when (state) {
            is TileState.Lessons -> return lessons(state, at)
            is TileState.Rest -> rest(state.headline, state.nextLabel)
            TileState.Empty, null -> rest("Nessun orario", "Apri l'app per scaricarlo")
        }
        return root(content)
    }

    // ---- Schermate di riposo ("Sei libero", "Buon weekend") ----

    private fun rest(headline: String, detail: String): LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(label(headline, Typography.TITLE_LARGE, colors.primary))
            .addContent(spacer(6f))
            .addContent(label(detail, Typography.BODY_SMALL, colors.onSurfaceVariant, maxLines = 2))
            .build()

    // ---- Giornata di lezioni ----

    private fun lessons(state: TileState.Lessons, at: LocalDateTime): LayoutElement {
        val agenda = state.agenda
        val minute = if (state.live) at.hour * 60 + at.minute else -1
        // Le ore buche prima della prima lezione e dopo l'ultima non si mostrano
        val first = agenda.lessons.first().start
        val last = agenda.lessons.last().end
        val slots = agenda.slots.filter { it.start >= first && it.end <= last }
        val hours = slots.filterIsInstance<LessonSlot>()

        // In evidenza: l'ora in corso, oppure (prima delle lezioni o in pausa) la prossima
        val featured = hours.indexOfFirst { minute < it.end }.coerceAtLeast(0)
        // Le ore passate spariscono: restano l'eventuale pausa in corso e quello che deve ancora venire
        val visible = slots.filter { it.end > minute }

        // Quante ore stanno nello schermo: in fondo le ultime si riassumono in "+ altre N"
        val available = contentHeight - (if (state.title != null) TITLE_HEIGHT else 0f)
        fun heightOf(slot: Slot) = when {
            slot is BreakSlot -> PAUSE_HEIGHT
            slot == hours[featured] -> BIG_HEIGHT
            slot == hours.getOrNull(featured + 1) -> NEXT_HEIGHT
            else -> SMALL_HEIGHT
        }
        var shown = visible
        while (shown.size > 1 && shown.sumOf { heightOf(it).toDouble() } + (shown.size - 1) * GAP +
            (if (shown.size < visible.size) MORE_HEIGHT + GAP else 0f) > available
        ) {
            shown = shown.dropLast(1).dropLastWhile { it is BreakSlot }
        }
        val hidden = visible.drop(shown.size).count { it is LessonSlot }

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        state.title?.let {
            column.addContent(label(it, Typography.LABEL_MEDIUM, colors.primary))
            column.addContent(spacer(TITLE_HEIGHT - 14f))
        }
        shown.forEachIndexed { index, slot ->
            if (index > 0) column.addContent(spacer(GAP))
            column.addContent(
                when {
                    slot is BreakSlot -> pauseRow(slot, active = minute in slot)
                    slot == hours[featured] -> featuredCard(slot as LessonSlot, agenda.date, minute, state.live, BIG_HEIGHT)
                    slot == hours.getOrNull(featured + 1) -> compactCard(slot as LessonSlot, NEXT_HEIGHT, Typography.LABEL_MEDIUM)
                    else -> compactCard(slot as LessonSlot, SMALL_HEIGHT, Typography.LABEL_SMALL)
                }
            )
        }
        if (hidden > 0) {
            column.addContent(spacer(GAP))
            column.addContent(label(if (hidden == 1) "+ 1 altra ora" else "+ altre $hidden ore", Typography.LABEL_SMALL, colors.onSurfaceVariant))
        }
        return root(column.build())
    }

    /** L'ora in evidenza: classe grande, aula e docente, e i minuti (che scorrono da soli). */
    private fun featuredCard(slot: LessonSlot, date: LocalDate, minute: Int, live: Boolean, height: Float): LayoutElement {
        val lesson = slot.lesson
        val running = live && minute in slot
        val countdown: LayoutElement = when {
            // In corso: minuti alla fine; prima dell'inizio: minuti all'inizio. Calcolati dall'orologio
            live -> label(
                minutesUntil(date.atTime(if (running) slot.end / 60 else slot.start / 60, if (running) slot.end % 60 else slot.start % 60),
                    prefix = if (running) "" else "tra "),
                Typography.LABEL_MEDIUM,
                if (lesson != null) colors.onPrimaryContainer else colors.primary,
            )
            else -> label(formatMinutes(slot.start), Typography.LABEL_MEDIUM, colors.onPrimaryContainer)
        }
        val title = lesson?.title ?: "Libera"
        val details = lesson?.let { listOfNotNull(it.room, it.teacher).joinToString(" · ") }
            ?.ifEmpty { null } ?: "${formatMinutes(slot.start)}–${formatMinutes(slot.end)}"
        val textColor = if (lesson != null) colors.onPrimaryContainer else colors.onSurface

        val info = LayoutElementBuilders.Column.Builder()
            .setWidth(weight(1f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(label(title, Typography.TITLE_MEDIUM, textColor, align = LayoutElementBuilders.TEXT_ALIGN_START))
            .addContent(label(details, Typography.LABEL_SMALL, textColor, align = LayoutElementBuilders.TEXT_ALIGN_START))
            .build()
        val row = LayoutElementBuilders.Row.Builder()
            .setWidth(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(info)
            .addContent(countdown)
            .build()
        return card(row, height, filled = lesson != null, primary = true, radius = FEATURED_RADIUS)
    }

    /** Ore non in evidenza: una riga "12:10 · 5D · 2.03", con l'icona se c'è un altro docente. */
    private fun compactCard(slot: LessonSlot, height: Float, typography: Int): LayoutElement {
        val lesson = slot.lesson
        val color = colors.onSurface
        val text = listOfNotNull(formatMinutes(slot.start), lesson?.title ?: "Libera", lesson?.room).joinToString(" · ")
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(label(text, typography, color))
        if (lesson?.teacher != null) {
            row.addContent(spacer(4f, horizontal = true))
            row.addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(PERSON_ICON)
                    .setWidth(dp(10f))
                    .setHeight(dp(10f))
                    .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(color.prop).build())
                    .build()
            )
        }
        return card(row.build(), height, filled = lesson != null, primary = false, centered = true)
    }

    /** Intervallo e pranzo: separatore di testo, colorato quando è il suo turno. */
    private fun pauseRow(slot: BreakSlot, active: Boolean) = label(
        "${slot.kind.label} · ${formatMinutes(slot.start)}",
        Typography.LABEL_SMALL,
        if (active) colors.primary else colors.onSurfaceVariant,
    )

    /** Rettangolo arrotondato: pieno per le lezioni, solo bordo per le ore buche. */
    private fun card(
        content: LayoutElement,
        height: Float,
        filled: Boolean,
        primary: Boolean,
        centered: Boolean = false,
        radius: Float = height / 2f,
    ): LayoutElement {
        val background = when {
            !filled -> null
            primary -> colors.primaryContainer
            else -> colors.surfaceContainer
        }
        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(12f)).setEnd(dp(12f)).build())
        val corner = ModifiersBuilders.Corner.Builder().setRadius(dp(radius)).build()
        if (background != null) {
            modifiers.setBackground(ModifiersBuilders.Background.Builder().setColor(background.prop).setCorner(corner).build())
        } else {
            modifiers.setBackground(ModifiersBuilders.Background.Builder().setCorner(corner).build())
            modifiers.setBorder(
                ModifiersBuilders.Border.Builder()
                    .setWidth(dp(1f))
                    .setColor((if (primary) colors.primary else colors.outlineVariant).prop)
                    .build()
            )
        }
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(dp(height))
            .setHorizontalAlignment(
                if (centered) LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER else LayoutElementBuilders.HORIZONTAL_ALIGN_START
            )
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(modifiers.build())
            .addContent(content)
            .build()
    }

    // ---- Struttura e testi ----

    /** Contenuto al centro con i margini dello schermo rotondo; toccando si apre l'app. */
    private fun root(content: LayoutElement): LayoutElement {
        val inset = ModifiersBuilders.Padding.Builder()
            .setStart(dp(screenHeightDp * HORIZONTAL_INSET)).setEnd(dp(screenHeightDp * HORIZONTAL_INSET))
            .setTop(dp(screenHeightDp * VERTICAL_INSET)).setBottom(dp(screenHeightDp * VERTICAL_INSET))
            .build()
        val centered = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setPadding(inset).build())
            .addContent(content)
            .build()
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(clickable(open)).build())
        return root.addContent(centered).build()
    }

    private fun label(
        content: String,
        typography: Int,
        color: LayoutColor,
        maxLines: Int = 1,
        align: Int = LayoutElementBuilders.TEXT_ALIGN_CENTER,
    ) = label(content.layoutString, typography, color, maxLines, align)

    private fun label(
        content: LayoutString,
        typography: Int,
        color: LayoutColor,
        maxLines: Int = 1,
        align: Int = LayoutElementBuilders.TEXT_ALIGN_CENTER,
    ) = scope.text(text = content, typography = typography, color = color, maxLines = maxLines, alignment = align)

    /** "37 min" o "tra 12 min", arrotondato per eccesso e aggiornato dall'orologio senza l'app. */
    private fun minutesUntil(target: LocalDateTime, prefix: String): LayoutString {
        val instant = target.atZone(ZoneId.systemDefault()).toInstant().plusSeconds(59)
        val minutes = DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicInstant.withSecondsPrecision(instant))
            .toIntMinutes()
            .format()
        val dynamic = DynamicString.constant(prefix).concat(minutes).concat(DynamicString.constant(" min"))
        return LayoutString("$prefix-- min", dynamic, StringLayoutConstraint.Builder("${prefix}00 min").build())
    }

    private fun spacer(size: Float, horizontal: Boolean = false): LayoutElement {
        val spacer = LayoutElementBuilders.Spacer.Builder()
        if (horizontal) spacer.setWidth(dp(size)) else spacer.setHeight(dp(size))
        return spacer.build()
    }

    private operator fun Slot.contains(minute: Int) = minute >= start && minute < end

    companion object {
        const val PERSON_ICON = "person"
        private const val GAP = 2f
        private const val PAUSE_HEIGHT = 14f
        private const val BIG_HEIGHT = 56f
        private const val FEATURED_RADIUS = 16f
        private const val NEXT_HEIGHT = 22f
        // Sotto i 18 dp il testo delle pillole viene tagliato
        private const val SMALL_HEIGHT = 18f
        private const val MORE_HEIGHT = 14f
        private const val TITLE_HEIGHT = 18f
        private const val VERTICAL_INSET = 0.11f
        private const val HORIZONTAL_INSET = 0.12f
    }
}
