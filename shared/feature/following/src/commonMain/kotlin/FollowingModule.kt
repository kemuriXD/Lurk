package io.github.kemurixd.lurk.feature.following

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Binds [FollowingViewModel]. Needs the app's `List<StreamingPlatform>`, `AccountsState`, `SettingsStore`, the
 * `DispatcherProvider` and the shared `LastLiveLoader`.
 *
 * A lambda, not `viewModelOf`, so the refresh interval keeps its default instead of being resolved from Koin.
 */
val followingModule: Module =
    module {
        viewModel { FollowingViewModel(get(), get(), get(), get(), get()) }
    }
