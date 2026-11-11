package io.github.lk024.orario.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

// Le settimane sono pagine: quella centrale è la settimana corrente, le altre si raggiungono con uno swipe
private const val WEEK_PAGES = 2001
private const val CURRENT_WEEK_PAGE = WEEK_PAGES / 2

/** Quante settimane dista dalla corrente la pagina [page]. */
fun weekOffsetOf(page: Int) = page - CURRENT_WEEK_PAGE

/**
 * Pager delle settimane legato a [weekOffset]: lo swipe cambia la settimana mostrata, e quando la
 * settimana cambia da fuori (tasto Oggi, cambio di giorno) il pager scorre fino a lei.
 */
@Composable
fun rememberWeekPagerState(weekOffset: Int, onWeekOffsetChange: (Int) -> Unit): PagerState {
    val state = rememberPagerState(initialPage = CURRENT_WEEK_PAGE + weekOffset) { WEEK_PAGES }
    val onChange by rememberUpdatedState(onWeekOffsetChange)
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { onChange(weekOffsetOf(it)) }
    }
    LaunchedEffect(weekOffset) {
        val target = CURRENT_WEEK_PAGE + weekOffset
        if (state.settledPage != target) state.animateScrollToPage(target)
    }
    return state
}
