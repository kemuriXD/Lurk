package io.github.kemurixd.lurk.feature.following

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Binds [FollowingViewModel]. Needs every `StreamingPlatform`, `AccountsState`, `SettingsStore` and the
 * `DispatcherProvider`.
 *
 * Not `viewModelOf`: the platforms are bound one by one and collected with `getAll()`, which a constructor reference
 * can't express, and the refresh interval keeps its default.
 */
val followingModule: Module =
    module {
        viewModel { FollowingViewModel(getAll(), get(), get(), get()) }
    }
