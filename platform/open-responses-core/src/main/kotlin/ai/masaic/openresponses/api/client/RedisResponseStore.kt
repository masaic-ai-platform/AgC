package ai.masaic.openresponses.api.client

import ai.masaic.openresponses.api.config.RedisStoreConfig
import ai.masaic.openresponses.api.model.InputMessageItem
import ai.masaic.openresponses.tool.ToolRequestContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseInputItem
import kotlinx.coroutines.reactive.awaitFirstOrNull
import mu.KotlinLogging
import org.redisson.api.RedissonReactiveClient
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

class RedisResponseStore(
    private val redissonClient: RedissonReactiveClient,
    private val objectMapper: ObjectMapper,
    private val config: RedisStoreConfig,
) : ResponseStore {
    private val logger = KotlinLogging.logger {}

    data class ResponseDocument(
        val responseJson: String,
        val inputItems: List<InputMessageItem>,
        val outputInputItems: List<InputMessageItem>,
    )

    override suspend fun storeResponse(
        response: Response,
        inputItems: List<ResponseInputItem>,
        context: ToolRequestContext,
    ) {
        val responseId = response.id()
        val key = responseKey(responseId)
        val lock = redissonClient.getLock("$key:lock")
        lock.lock().awaitFirstOrNull()
        try {
            val newInputItems = inputItems.map { objectMapper.convertValue(it, InputMessageItem::class.java) }
            val newOutputItems = response.output().mapNotNull { outputItem ->
                when {
                    outputItem.isMessage() && outputItem.message().orElse(null) != null ->
                        objectMapper.convertValue(outputItem.message().get(), InputMessageItem::class.java)
                    outputItem.isFunctionCall() -> {
                        val functionCall = outputItem.asFunctionCall()
                        InputMessageItem(
                            id = functionCall.id().getOrNull() ?: functionCall.callId(),
                            role = "assistant",
                            type = "function_call",
                            call_id = functionCall.callId(),
                            name = functionCall.name(),
                            arguments = functionCall.arguments(),
                        )
                    }
                    else -> null
                }
            }
            val existing = readDocument(key)
            val document = ResponseDocument(
                responseJson = objectMapper.writeValueAsString(response),
                inputItems = (existing?.inputItems.orEmpty() + newInputItems).distinct(),
                outputInputItems = (existing?.outputInputItems.orEmpty() + newOutputItems).distinct(),
            )
            writeDocument(key, document)
        } finally {
            lock.unlock().awaitFirstOrNull()
        }
    }

    override suspend fun getResponse(responseId: String): Response? =
        readDocument(responseKey(responseId))?.let { objectMapper.readValue(it.responseJson, Response::class.java) }

    override suspend fun getInputItems(responseId: String): List<InputMessageItem> =
        readDocument(responseKey(responseId))?.inputItems ?: emptyList()

    override suspend fun getOutputItems(responseId: String): List<InputMessageItem> =
        readDocument(responseKey(responseId))?.outputInputItems ?: emptyList()

    override suspend fun deleteResponse(responseId: String): Boolean {
        val key = responseKey(responseId)
        return redissonClient.getBucket<String>(key).delete().awaitFirstOrNull() == true
    }

    private suspend fun readDocument(key: String): ResponseDocument? {
        val bucket = redissonClient.getBucket<String>(key)
        val json = bucket.get().awaitFirstOrNull() ?: return null
        bucket.expire(ttl()).awaitFirstOrNull()
        return objectMapper.readValue(json, ResponseDocument::class.java)
    }

    private suspend fun writeDocument(key: String, document: ResponseDocument) {
        redissonClient.getBucket<String>(key)
            .set(objectMapper.writeValueAsString(document), ttl())
            .awaitFirstOrNull()
        logger.debug { "Stored response in Redis with key $key" }
    }

    private fun responseKey(id: String) = "${config.keyPrefix}:response:$id"
    private fun ttl() = Duration.ofMinutes(config.ttlMinutes)
}
