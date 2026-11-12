package io.github.kemurixd.lurk.platform.kick.account

import io.github.kemurixd.lurk.core.common.LurkError
import io.github.kemurixd.lurk.core.common.Outcome
import io.github.kemurixd.lurk.core.model.Account
import io.github.kemurixd.lurk.core.model.Platform
import io.github.kemurixd.lurk.platform.kick.api.Rig
import io.github.kemurixd.lurk.platform.kick.api.json
import io.github.kemurixd.lurk.platform.kick.api.mockEngine
import io.github.kemurixd.lurk.platform.kick.fixture
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KickProfileFetcherTest {
    @Test
    fun profileMapsAccount() =
        runTest {
            val seen = mutableListOf<HttpRequestData>()
            val api =
                Rig(
                    mockEngine {
                        seen += it
                        json(fixture("account/user.json"))
                    },
                ).api
            val result = KickProfileFetcher(api).profile("some-token")

            val expected = Account(Platform.KICK, "1000099", "viewer1", "Viewer One", "https://cdn.example/a/v1.png")
            assertEquals(Outcome.Success(expected), result)
            assertEquals("/api/v1/user", seen.single().url.encodedPath)
            assertEquals("Bearer some-token", seen.single().headers[HttpHeaders.Authorization])
        }

    @Test
    fun loginFallsBackToLowercasedUsername() =
        runTest {
            val body = fixture("account/user.json").replace("\"slug\": \"viewer1\"", "\"slug\": null")
            val api = Rig(mockEngine { json(body) }).api
            val account = (KickProfileFetcher(api).profile("some-token") as Outcome.Success).value

            assertEquals("viewer one", account.login)
            assertEquals("Viewer One", account.displayName)
        }

    @Test
    fun unauthorizedIsAuthExpired() =
        runTest {
            val api = Rig(mockEngine { json("""{"message":"Unauthenticated."}""", HttpStatusCode.Unauthorized) }).api

            assertEquals(Outcome.Failure(LurkError.AuthExpired), KickProfileFetcher(api).profile("some-token"))
        }

    @Test
    fun replyWithoutIdIsSchemaChanged() =
        runTest {
            val body = fixture("account/user.json").replace("\"id\": 1000099,\n  \"email\"", "\"email\"")
            val api = Rig(mockEngine { json(body) }).api

            assertEquals(Outcome.Failure(LurkError.SchemaChanged), KickProfileFetcher(api).profile("some-token"))
        }

    @Test
    fun platformIsKick() =
        runTest {
            val api = Rig(mockEngine { json(fixture("account/user.json")) }).api

            assertEquals(Platform.KICK, KickProfileFetcher(api).platform)
        }
}
