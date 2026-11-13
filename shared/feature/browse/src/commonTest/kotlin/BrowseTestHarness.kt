package io.github.kemurixd.lurk.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.reflect.KClass

/** Builds view models through a [ViewModelStore] so a test can clear them like the framework would. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BrowseTestHarness {
    val dispatcher = StandardTestDispatcher()
    val store = ViewModelStore()
    private var created = 0

    fun <T : ViewModel> create(
        type: KClass<T>,
        build: () -> T,
    ): T {
        val factory = viewModelFactory { addInitializer(type) { build() } }
        return ViewModelProvider.create(store, factory)["vm${created++}", type]
    }
}
