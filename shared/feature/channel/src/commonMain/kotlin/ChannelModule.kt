package io.github.kemurixd.lurk.feature.channel

import io.github.kemurixd.lurk.core.model.ChannelRef
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.api.StreamingPlatform
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Binds [ChannelViewModel], built with `parametersOf(platform, login)`. Needs the app's `List<StreamingPlatform>`,
 * `AccountsState` and `DispatcherProvider`.
 */
val channelModule: Module =
    module {
        viewModel { params ->
            val platform: Platform = params.get()
            val login: String = params.get()
            ChannelViewModel(
                platform = get<List<StreamingPlatform>>().first { it.id == platform },
                ref = ChannelRef(platform, login),
                accounts = get(),
                dispatchers = get(),
            )
        }
    }
