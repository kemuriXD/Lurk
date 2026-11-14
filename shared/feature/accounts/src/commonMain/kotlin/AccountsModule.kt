package io.github.kemurixd.lurk.feature.accounts

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Binds [AccountsViewModel] and [LoginViewModel], the second built with `parametersOf(platform)`. Needs the app's
 * `AccountsState`, `SessionRepository` and `DispatcherProvider`.
 */
val accountsModule: Module =
    module {
        viewModelOf(::AccountsViewModel)
        viewModel { params -> LoginViewModel(platform = params.get(), sessions = get(), dispatchers = get()) }
    }
