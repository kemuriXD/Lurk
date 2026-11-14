package io.github.kemurixd.lurk.feature.search

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Binds [SearchViewModel]. Needs the app's `List<StreamingPlatform>`, the `DispatcherProvider` and the shared
 * `LastLiveLoader`.
 *
 * A lambda, not `viewModelOf`, so the debounce keeps its default instead of being resolved from Koin.
 */
val searchModule: Module =
    module {
        viewModel { SearchViewModel(get(), get(), get()) }
    }
