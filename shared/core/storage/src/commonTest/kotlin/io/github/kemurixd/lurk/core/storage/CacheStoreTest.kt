package io.github.kemurixd.lurk.core.storage

import io.github.kemurixd.lurk.core.common.DispatcherProvider
import io.github.kemurixd.lurk.core.storage.db.LurkDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CacheStoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val driver = inMemoryDriver()
    private val store = CacheStore(LurkDatabase(driver), TestDispatchers(dispatcher))

    private val t = Instant.fromEpochMilliseconds(1_762_800_000_000)

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun putThenGetReturnsTheValueAndItsFetchTime() =
        scope.runTest {
            store.put("k", "v", t)

            assertEquals(CachedValue("v", t), store.get("k"))
        }

    @Test
    fun secondPutReplacesTheFirst() =
        scope.runTest {
            val later = Instant.fromEpochMilliseconds(1_762_800_060_000)
            store.put("k", "first", t)
            store.put("k", "second", later)

            assertEquals(CachedValue("second", later), store.get("k"))
        }

    @Test
    fun missingKeyReturnsNull() =
        scope.runTest {
            assertNull(store.get("absent"))
        }

    @Test
    fun clearRemovesEveryEntry() =
        scope.runTest {
            store.put("k", "v", t)
            store.put("other", "w", t)

            store.clear()

            assertNull(store.get("k"))
            assertNull(store.get("other"))
        }
}

private class TestDispatchers(
    dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val io = dispatcher
    override val default = dispatcher
    override val main = dispatcher
}
