package io.github.radixtrator.resync.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

/**
 * A list's scroll position, kept in its screen's ViewModel.
 *
 * In tablet mode's dual pane, slide-to-go-back draws the page underneath as a second composition of it while you
 * drag. When the drag pops, the nav host composes that page again in the same frame, before the drag's copy has
 * handed its saved state back, so a list relying on rememberSaveable alone would open at the top. Both copies share
 * the ViewModel, so the new one starts from here instead.
 */
class ScrollMemory {
    var index = 0
    var offset = 0
}

@Composable
fun rememberLazyListState(memory: ScrollMemory): LazyListState {
    val state = rememberLazyListState(memory.index, memory.offset)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            memory.index = index
            memory.offset = offset
        }
    }
    return state
}

@Composable
fun rememberLazyStaggeredGridState(memory: ScrollMemory): LazyStaggeredGridState {
    val state = rememberLazyStaggeredGridState(memory.index, memory.offset)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            memory.index = index
            memory.offset = offset
        }
    }
    return state
}
