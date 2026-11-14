package io.github.kemurixd.lurk.core.network

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.LurkException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineGuardTest {
    private class FakeStatus(
        online: Boolean,
    ) : NetworkStatus {
        override val isOnline: MutableStateFlow<Boolean> = MutableStateFlow(online)
    }

    @Test
    fun noNetworkFailsAsOfflineWithoutSendingARequest() =
        runTest {
            var sent = 0
            val engine =
                MockEngine {
                    sent++
                    respondOk("hi")
                }
            val client = createHttpClient(engine, network = FakeStatus(online = false))
            val e = assertFailsWith<LurkException> { client.get("https://example.test/api") }
            assertEquals(LurkError.Offline, e.error)
            assertEquals(0, sent)
        }

    @Test
    fun anOnlineNetworkSendsTheRequest() =
        runTest {
            val client = createHttpClient(MockEngine { respondOk("hi") }, network = FakeStatus(online = true))
            assertEquals("hi", client.get("https://example.test/api").bodyAsText())
        }

    @Test
    fun theClientRecoversWhenTheNetworkComesBack() =
        runTest {
            var sent = 0
            val status = FakeStatus(online = false)
            val client =
                createHttpClient(
                    MockEngine {
                        sent++
                        respondOk("hi")
                    },
                    network = status,
                )
            assertFailsWith<LurkException> { client.get("https://example.test/api") }
            status.isOnline.value = true
            assertEquals("hi", client.get("https://example.test/api").bodyAsText())
            assertEquals(1, sent)
        }

    @Test
    fun theDefaultNetworkIsAlwaysOnline() {
        val status: StateFlow<Boolean> = AlwaysOnline.isOnline
        assertEquals(true, status.value)
    }

    @Test
    fun aDroppedNetworkStopsTheNextRequestOfAnOpenClient() =
        runTest {
            var sent = 0
            val status = FakeStatus(online = true)
            val client =
                createHttpClient(
                    MockEngine {
                        sent++
                        respondOk("hi")
                    },
                    network = status,
                )
            client.get("https://example.test/api")
            status.isOnline.value = false
            assertFailsWith<LurkException> { client.get("https://example.test/api") }
            assertEquals(1, sent)
        }
}
