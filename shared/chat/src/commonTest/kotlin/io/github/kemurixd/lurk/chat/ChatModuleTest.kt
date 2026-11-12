package io.github.kemurixd.lurk.chat

import io.github.kemurixd.lurk.core.common.DefaultDispatchers
import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.emotes.EmoteSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Clock

@OptIn(KoinExperimentalAPI::class)
class ChatModuleTest {
    @Test
    fun chatModuleIsCompleteGivenCommonBindings() {
        chatModule.verify(extraTypes = listOf(DispatcherProvider::class, Clock::class))
    }

    @Test
    fun factoryBuildsASessionPerCallAndTheTokenizerIsShared() {
        val app =
            koinApplication {
                modules(module { single<DispatcherProvider> { DefaultDispatchers } }, chatModule)
            }
        val factory = app.koin.get<ChatSessionFactory>()
        assertSame(factory, app.koin.get<ChatSessionFactory>())
        assertSame(app.koin.get<Tokenizer>(), app.koin.get<Tokenizer>())
        val channel = ChatChannel(Platform.TWITCH, "1000500", "somechannel")
        val emotes = MutableStateFlow(EmoteSnapshot.EMPTY)
        val prefs = MutableStateFlow(ChatPreferences())
        val a = factory.create(channel, { emptyFlow() }, null, emotes, prefs, null)
        val b = factory.create(channel, { emptyFlow() }, null, emotes, prefs, "viewer")
        assertNotSame(a, b)
    }
}
