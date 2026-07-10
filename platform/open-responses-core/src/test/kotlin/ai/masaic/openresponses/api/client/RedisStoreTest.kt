package ai.masaic.openresponses.api.client

import ai.masaic.openresponses.api.config.RedisStoreConfig
import ai.masaic.openresponses.api.model.InputMessageItem
import ai.masaic.openresponses.tool.ToolRequestContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseInputItem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.redisson.api.RBucketReactive
import org.redisson.api.RedissonReactiveClient
import reactor.core.publisher.Mono
import java.time.Duration

class RedisResponseStoreTest {
    private val client = mockk<RedissonReactiveClient>(relaxed = true)
    private val bucket = mockk<RBucketReactive<String>>(relaxed = true)
    private val objectMapper = mockk<ObjectMapper>(relaxed = true)
    private val config = RedisStoreConfig(keyPrefix = "test", ttlMinutes = 15)
    private val store = RedisResponseStore(client, objectMapper, config)

    @Test
    fun `storeResponse merges items and does not acquire a lock`() =
        runTest {
            val response = mockk<Response>()
            val incoming = mockk<ResponseInputItem>()
            val existingItem = InputMessageItem(id = "existing")
            val incomingItem = InputMessageItem(id = "incoming")
            val existingDocument =
                RedisResponseStore.ResponseDocument(
                    responseJson = "existing-response",
                    inputItems = listOf(existingItem),
                    outputInputItems = emptyList(),
                )
            val storedDocument =
                RedisResponseStore.ResponseDocument(
                    responseJson = "response-json",
                    inputItems = listOf(existingItem, incomingItem),
                    outputInputItems = emptyList(),
                )

            every { response.id() } returns "resp-1"
            every { response.output() } returns emptyList()
            every { client.getBucket<String>("test:response:resp-1") } returns bucket
            every { bucket.get() } returns Mono.just("existing-document")
            every { bucket.expire(Duration.ofMinutes(15)) } returns Mono.just(true)
            every { bucket.set(any<String>(), Duration.ofMinutes(15)) } returns Mono.empty()
            every { objectMapper.convertValue(incoming, InputMessageItem::class.java) } returns incomingItem
            every { objectMapper.writeValueAsString(response) } returns "response-json"
            every { objectMapper.writeValueAsString(storedDocument) } returns "stored-document"
            every { objectMapper.readValue("existing-document", RedisResponseStore.ResponseDocument::class.java) } returns existingDocument

            store.storeResponse(response, listOf(incoming), mockk<ToolRequestContext>())

            verify(exactly = 0) { client.getLock(any<String>()) }
            verify { bucket.set(any<String>(), Duration.ofMinutes(15)) }
        }

    @Test
    fun `getInputItems refreshes ttl and returns empty for missing response`() =
        runTest {
            every { client.getBucket<String>("test:response:missing") } returns bucket
            every { bucket.get() } returns Mono.empty()

            assertTrue(store.getInputItems("missing").isEmpty())
            verify(exactly = 0) { bucket.expire(any<Duration>()) }
        }

    @Test
    fun `deleteResponse returns deletion result`() =
        runTest {
            every { client.getBucket<String>("test:response:resp-1") } returns bucket
            every { bucket.delete() } returns Mono.just(true)

            assertTrue(store.deleteResponse("resp-1"))
        }
}

class RedisCompletionStoreTest {
    private val client = mockk<RedissonReactiveClient>(relaxed = true)
    private val bucket = mockk<RBucketReactive<String>>(relaxed = true)
    private val objectMapper = mockk<ObjectMapper>(relaxed = true)
    private val config = RedisStoreConfig(keyPrefix = "test", ttlMinutes = 15)
    private val store = RedisCompletionStore(client, objectMapper, config)

    @Test
    fun `store and retrieve completion data`() =
        runTest {
            val completion = mockk<ChatCompletion>()
            val messages = listOf(mockk<ChatCompletionMessageParam>())
            val document =
                RedisCompletionStore.CompletionDocument(
                    completionJson = "completion-json",
                    messagesJson = "messages-json",
                    aliasMapJson = "{}",
                )

            every { completion.id() } returns "completion-1"
            every { client.getBucket<String>("test:completion:completion-1") } returns bucket
            every { bucket.set(any<String>(), Duration.ofMinutes(15)) } returns Mono.empty()
            every { bucket.get() } returns Mono.just("stored-document")
            every { bucket.expire(Duration.ofMinutes(15)) } returns Mono.just(true)
            every { objectMapper.writeValueAsString(completion) } returns "completion-json"
            every { objectMapper.writeValueAsString(messages) } returns "messages-json"
            every { objectMapper.writeValueAsString(emptyMap<String, String>()) } returns "{}"
            every { objectMapper.writeValueAsString(document) } returns "stored-document"
            every { objectMapper.readValue("stored-document", RedisCompletionStore.CompletionDocument::class.java) } returns document
            every { objectMapper.readValue("completion-json", ChatCompletion::class.java) } returns completion
            every {
                objectMapper.readValue<List<ChatCompletionMessageParam>>(
                    "messages-json",
                    any<com.fasterxml.jackson.databind.JavaType>(),
                )
            } returns messages

            assertEquals(completion, store.storeCompletion(completion, messages))
            assertEquals(completion, store.getCompletion("completion-1"))
            assertEquals(messages, store.getMessages("completion-1"))
            verify { bucket.expire(Duration.ofMinutes(15)) }
        }

    @Test
    fun `missing and deleted completions are reported correctly`() =
        runTest {
            every { client.getBucket<String>("test:completion:missing") } returns bucket
            every { bucket.get() } returns Mono.empty()
            every { bucket.delete() } returns Mono.just(false)

            assertNull(store.getCompletion("missing"))
            assertFalse(store.deleteCompletion("missing"))
        }
}
