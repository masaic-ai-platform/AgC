package ai.masaic.openresponses.api.utils

/**
 * Coroutine context element carrying request-scoped identifiers for AgC loop execution.
 *
 * Values are optional; when absent, getters return null.
 */
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class AgCLoopContext(
    val userId: String? = null,
    val sessionId: String? = null,
    val loopId: String? = null,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AgCLoopContext> {
        /**
         * Retrieve the current [AgCLoopContext] from the coroutine context, if present.
         */
        suspend fun current(): AgCLoopContext? = coroutineContext[AgCLoopContext]

        /**
         * Get the current user id from [AgCLoopContext] or null if not set.
         */
        suspend fun userId(): String? = current()?.userId

        /**
         * Get the current session id from [AgCLoopContext] or null if not set.
         */
        suspend fun sessionId(): String? = current()?.sessionId

        /**
         * Get the current loop id from [AgCLoopContext] or null if not set.
         */
        suspend fun loopId(): String? = current()?.loopId

        /**
         * Package the current loop context into a [LoopContextInfo].
         */
        suspend fun toLoopContextInfo(): LoopContextInfo = LoopContextInfo(userId(), sessionId(), loopId())
    }
}

data class LoopContextInfo(
    val userId: String? = null,
    val sessionId: String? = null,
    val loopId: String? = null,
)
