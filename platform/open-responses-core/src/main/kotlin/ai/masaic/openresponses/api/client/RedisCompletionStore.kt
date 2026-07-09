package ai.masaic.openresponses.api.client

import ai.masaic.openresponses.api.config.RedisStoreConfig
import ai.masaic.openresponses.tool.CompletionToolRequestContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionMessageParam
import kotlinx.coroutines.reactive.awaitFirstOrNull
import mu.KotlinLogging
import org.redisson.api.RedissonReactiveClient
import java.time.Duration

class RedisCompletionStore(
    private val redissonClient: RedissonReactiveClient,
    private val objectMapper: ObjectMapper,
    private val config: RedisStoreConfig,
) : CompletionStore {
    private val logger = KotlinLogging.logger {}

    data class CompletionDocument(
        val completionJson: String,
        val messagesJson: String,
        val aliasMapJson: String,
    )

    override suspend fun storeCompletion(
        completion: ChatCompletion,
        messages: List<ChatCompletionMessageParam>,
        context: CompletionToolRequestContext?,
    ): ChatCompletion {
        val completionId = completion.id()
        if (completionId.isBlank()) {
            logger.warn { "Attempted to store completion without a valid ID. Skipping." }
            return completion
        }
        val document = CompletionDocument(
            completionJson = objectMapper.writeValueAsString(completion),
            messagesJson = objectMapper.writeValueAsString(messages),
            aliasMapJson = objectMapper.writeValueAsString(context?.aliasMap ?: emptyMap<String, String>()),
        )
        val key = completionKey(completionId)
        redissonClient.getBucket<String>(key)
            .set(objectMapper.writeValueAsString(document), ttl())
            .awaitFirstOrNull()
        return completion
    }

    override suspend fun getCompletion(completionId: String): ChatCompletion? =
        readDocument(completionId)?.let { objectMapper.readValue(it.completionJson, ChatCompletion::class.java) }

    override suspend fun getMessages(completionId: String): List<ChatCompletionMessageParam>? =
        readDocument(completionId)?.let {
            objectMapper.readValue(it.messagesJson, objectMapper.typeFactory.constructCollectionType(List::class.java, ChatCompletionMessageParam::class.java))
        }

    override suspend fun deleteCompletion(completionId: String): Boolean =
        redissonClient.getBucket<String>(completionKey(completionId)).delete().awaitFirstOrNull() == true

    private suspend fun readDocument(completionId: String): CompletionDocument? {
        val bucket = redissonClient.getBucket<String>(completionKey(completionId))
        val json = bucket.get().awaitFirstOrNull() ?: return null
        bucket.expire(ttl()).awaitFirstOrNull()
        return objectMapper.readValue(json, CompletionDocument::class.java)
    }

    private fun completionKey(id: String) = "${config.keyPrefix}:completion:$id"
    private fun ttl() = Duration.ofMinutes(config.ttlMinutes)
}
