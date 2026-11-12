package io.github.kemurixd.lurk.chat

import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

/** Binds the [Tokenizer] and the [ChatSessionFactory]. Needs a `DispatcherProvider` from the common module. */
val chatModule: Module =
    module {
        single { Tokenizer() }
        single { ChatSessionFactory(get(), get(), Clock.System) }
    }
