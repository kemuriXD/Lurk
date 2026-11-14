package io.github.kemurixd.lurk.feature.browse

import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Page
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** Keeps [state] subscribed for the rest of the test and lets the first loads run. */
internal fun <S> TestScope.watch(state: StateFlow<S>): StateFlow<S> {
    backgroundScope.launch { state.collect {} }
    runCurrent()
    return state
}

internal fun <T> pageOf(
    vararg items: T,
    next: String? = null,
): Outcome<Page<T>> = Outcome.Success(Page(items.toList(), next))
