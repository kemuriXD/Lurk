package io.github.kemurixd.lurk.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/** Result of an operation that can fail with a known [LurkError]. */
sealed interface Outcome<out T> {
    data class Success<T>(
        val value: T,
    ) : Outcome<T>

    data class Failure(
        val error: LurkError,
    ) : Outcome<Nothing>
}

fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.errorOrNull(): LurkError? = (this as? Outcome.Failure)?.error

/**
 * Runs [block] and maps known failures to [Outcome.Failure]. Cancellation and unexpected
 * exceptions are rethrown so bugs stay visible.
 */
suspend fun <T> outcomeOf(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: LurkException) {
        Outcome.Failure(e.error)
    } catch (
        @Suppress("SwallowedException") e: SerializationException,
    ) {
        Outcome.Failure(LurkError.SchemaChanged)
    }
