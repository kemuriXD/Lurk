package io.github.kemurixd.lurk.feature.browse

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Binds the three Browse view models. Needs the app's `List<StreamingPlatform>` and the `DispatcherProvider`.
 * [CategoryStreamsViewModel] takes its platform and category ID as `parametersOf(platform, id)`.
 */
val browseModule: Module =
    module {
        viewModel { TopLiveViewModel(get(), get()) }
        viewModel { CategoriesViewModel(get(), get()) }
        viewModel { params -> CategoryStreamsViewModel(params.get(), params.get(), get(), get()) }
    }
