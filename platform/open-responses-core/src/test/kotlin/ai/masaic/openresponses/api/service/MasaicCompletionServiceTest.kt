package ai.masaic.openresponses.api.service

import ai.masaic.openresponses.api.client.CompletionStore
import ai.masaic.openresponses.api.client.MasaicOpenAiCompletionServiceImpl
import ai.masaic.openresponses.api.model.CreateCompletionRequest
import ai.masaic.openresponses.api.model.InstrumentationMetadataInput
import ai.masaic.openresponses.api.support.service.TelemetryService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.mockk.*
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.codec.ServerSentEvent
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.util.LinkedMultiValueMap
import java.net.URI
import java.util.concurrent.TimeoutException

@ExtendWith(SpringExtension::class)
class MasaicCompletionServiceTest {
    private lateinit var openAICompletionService: MasaicOpenAiCompletionServiceImpl
    private lateinit var completionStore: CompletionStore
    private lateinit var objectMapper: ObjectMapper
    private lateinit var masaicCompletionService: MasaicCompletionService
    private val telemetryService = TelemetryService(ObservationRegistry.NOOP, OpenTelemetry.noop(), SimpleMeterRegistry())

    // Default values for testing
    private val defaultModel = "gpt-4o"
    private val defaultAuthHeader = "Bearer test-key"
    private val defaultTraceId = "test-trace-id"

    @BeforeEach
    fun setUp() {
        openAICompletionService = mockk()
        completionStore = mockk()
        objectMapper = jacksonObjectMapper() // Use real ObjectMapper for parsing messages

        // Mock static method if necessary (requires mockk-jvm setup in build.gradle)
        // mockkStatic(MasaicCompletionService::class)
        // every { MasaicCompletionService.getEnvVar(any()) } returns null // Default mock for env var

        masaicCompletionService =
            MasaicCompletionService(
                openAICompletionService,
                completionStore,
                objectMapper,
                telemetryService,
            )

        // Relaxed mock for ChatCompletion
        mockkConstructor(ChatCompletion::class)
        every { anyConstructed<ChatCompletion>().id() } returns "cmpl-123"
    }

    @AfterEach
    fun tearDown() {
        // unmockkStatic(MasaicCompletionService::class)
        clearAllMocks()
    }

    private fun createDefaultRequest(model: String = defaultModel): CreateCompletionRequest =
        CreateCompletionRequest(
            model = model,
            messages = listOf(mapOf("role" to "user", "content" to "Hello")),
            // Add other default parameters as needed
        )

    private fun createDefaultHeaders(auth: String? = defaultAuthHeader): LinkedMultiValueMap<String, String> {
        val headers = LinkedMultiValueMap<String, String>()
        auth?.let { headers.add("Authorization", it) }
        headers.add("X-B3-TraceId", defaultTraceId)
        return headers
    }
    
    // --- Test Nests will go here ---

    @Nested
    inner class CreateCompletionTests {
        @Test
        fun `should create completion successfully`() =
            runTest {
                // Given
                val request = createDefaultRequest()
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedCompletion = mockk<ChatCompletion>(relaxed = true)
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", defaultModel, "api.groq.com", "-1")

                coEvery {
                    openAICompletionService.create(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                    )
                } returns expectedCompletion

                // When
                val result = masaicCompletionService.createCompletion(request, headers, queryParams)

                // Then
                assertEquals(expectedCompletion, result)
                coVerify(exactly = 1) {
                    openAICompletionService.create(
                        any<OpenAIClient>(),
                        match { params ->
                            params.model().toString() == defaultModel &&
                                params
                                    .messages()
                                    .first()
                                    .asUser()
                                    .content()
                                    .asText() == "Hello"
                            // Add more parameter checks if needed
                        },
                        expectedMetadata,
                        any(),
                    )
                }
                // Verify storeCompletion is NOT called as it's commented out in the service
                // coVerify(exactly = 0) { completionStore.storeCompletion(any(), any()) }
            }

        @Test
        fun `should throw RuntimeException on timeout`() =
            runTest {
                // Given
                val request = createDefaultRequest()
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedMetadata = InstrumentationMetadataInput("openai", "api.openai.com")

                coEvery {
                    openAICompletionService.create(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                    )
                } throws RuntimeException("Timeout")

                // When & Then
                assertThrows<RuntimeException> {
                    masaicCompletionService.createCompletion(request, headers, queryParams)
                }
            }

        @Test
        fun `should throw CompletionTimeoutException on java TimeoutException`() =
            runTest {
                // Given
                val request = createDefaultRequest()
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", "gpt-4o", "api.groq.com", "-1")

                coEvery {
                    openAICompletionService.create(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                    )
                } throws TimeoutException("Timeout")

                // When & Then
                assertThrows<CompletionTimeoutException> {
                    masaicCompletionService.createCompletion(request, headers, queryParams)
                }
            }

        @Test
        fun `should throw RuntimeException on general error`() =
            runTest {
                // Given
                val request = createDefaultRequest()
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", "gpt-4o", "api.groq.com", "-1")
                val exception = RuntimeException("Something went wrong")

                coEvery {
                    openAICompletionService.create(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                    )
                } throws exception

                // When & Then
                val thrown =
                    assertThrows<RuntimeException> {
                        masaicCompletionService.createCompletion(request, headers, queryParams)
                    }
                assertEquals(exception.message, thrown.message)
            }

        @Test
        fun `should throw IllegalArgumentException if Authorization header is missing`() =
            runTest {
                // Given
                val request = createDefaultRequest()
                val headers = createDefaultHeaders(auth = null) // No Auth header
                val queryParams = LinkedMultiValueMap<String, String>()

                // When & Then
                val thrown =
                    assertThrows<IllegalArgumentException> {
                        masaicCompletionService.createCompletion(request, headers, queryParams)
                    }
                assertEquals("api-key is missing.", thrown.message)
            }

        @Test
        fun `should accept lowercase authorization header`() =
            runTest {
                // Given
                val request = createDefaultRequest()
                val headers =
                    createDefaultHeaders().also {
                        it.clear()
                        it.add("authorization", defaultAuthHeader)
                        it.add("X-B3-TraceId", defaultTraceId)
                    }
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedCompletion = mockk<ChatCompletion>(relaxed = true)

                coEvery {
                    openAICompletionService.create(any(), any(), any(), any())
                } returns expectedCompletion

                // When
                val result = masaicCompletionService.createCompletion(request, headers, queryParams)

                // Then
                assertSame(expectedCompletion, result)
                coVerify(exactly = 1) {
                    openAICompletionService.create(any(), any(), any(), any())
                }
            }

        @Test
        fun `should extract model name correctly when using provider format`() =
            runTest {
                // Given
                val modelWithProvider = "groq@llama3-8b-8192"
                val expectedModelName = "llama3-8b-8192"
                val request = createDefaultRequest(model = modelWithProvider)
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedCompletion = mockk<ChatCompletion>(relaxed = true)
                val expectedMetadata = InstrumentationMetadataInput("groq", "llama3-8b-8192", "api.groq.com", "-1") // Base URL derived from provider

                coEvery {
                    openAICompletionService.create(any(), any(), expectedMetadata, any())
                } returns expectedCompletion

                // When
                masaicCompletionService.createCompletion(request, headers, queryParams)

                // Then
                coVerify(exactly = 1) {
                    openAICompletionService.create(
                        any<OpenAIClient>(),
                        match { params -> params.model().toString() == expectedModelName },
                        expectedMetadata,
                        any(),
                    )
                }
            }

        @Test
        fun `should extract model name correctly when using URL format`() =
            runTest {
                // Given
                val modelWithUrl = "http://localhost:11434/v1@local-model"
                val expectedModelName = "local-model"
                val request = createDefaultRequest(model = modelWithUrl)
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedCompletion = mockk<ChatCompletion>(relaxed = true)
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", expectedModelName, "localhost", "11434")

                coEvery {
                    openAICompletionService.create(any(), any(), expectedMetadata, any())
                } returns expectedCompletion

                // When
                masaicCompletionService.createCompletion(request, headers, queryParams)

                // Then
                coVerify(exactly = 1) {
                    openAICompletionService.create(
                        any<OpenAIClient>(),
                        match { params -> params.model().toString() == expectedModelName },
                        expectedMetadata,
                        any(),
                    )
                }
            }
    }

    @Nested
    inner class CreateStreamingCompletionTests {
        @Test
        fun `should create streaming completion successfully`() =
            runTest {
                // Given
                val request = createDefaultRequest().copy(stream = true)
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", "gpt-4o", "api.groq.com", "-1")
                
                // Create mock streaming events
                val streamEvent1 = ServerSentEvent.builder("data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}").build()
                val streamEvent2 = ServerSentEvent.builder("data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" World\"}}]}").build()
                val streamEvent3 = ServerSentEvent.builder("data: [DONE]").build()
                val expectedFlow = flowOf(streamEvent1, streamEvent2, streamEvent3)

                coEvery {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                        any(),
                    )
                } returns expectedFlow

                // When
                val resultFlow = masaicCompletionService.createStreamingCompletion(request, headers, queryParams)

                // Then
                val collectedEvents = mutableListOf<ServerSentEvent<String>>()
                resultFlow.collect { event ->
                    collectedEvents.add(event)
                }
                
                assertEquals(3, collectedEvents.size)
                assertEquals(streamEvent1.data(), collectedEvents[0].data())
                assertEquals(streamEvent2.data(), collectedEvents[1].data())
                assertEquals(streamEvent3.data(), collectedEvents[2].data())
                
                coVerify(exactly = 1) {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        match { params ->
                            params.model().toString() == defaultModel &&
                                params
                                    .messages()
                                    .first()
                                    .asUser()
                                    .content()
                                    .asText() == "Hello"
                        },
                        expectedMetadata,
                        any(),
                        any(),
                    )
                }
            }

        @Test
        fun `should handle streaming completion with final response callback`() =
            runTest {
                // Given
                val request = createDefaultRequest().copy(stream = true)
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", "gpt-4o", "api.groq.com", "-1")
                val mockFinalCompletion = mockk<ChatCompletion>(relaxed = true)
                
                val streamEvent = ServerSentEvent.builder("data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\"}").build()
                val expectedFlow = flowOf(streamEvent)
                
                val callbackSlot = slot<(ChatCompletion) -> Unit>()
                
                coEvery {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                        capture(callbackSlot),
                    )
                } answers {
                    // Simulate the callback being invoked
                    callbackSlot.captured.invoke(mockFinalCompletion)
                    expectedFlow
                }

                // When
                val resultFlow = masaicCompletionService.createStreamingCompletion(request, headers, queryParams)
                resultFlow.collect { } // Consume the flow

                // Then
                coVerify(exactly = 1) {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                        any(),
                    )
                }
            }

        @Test
        fun `should throw RuntimeException on general error`() =
            runTest {
                // Given
                val request = createDefaultRequest().copy(stream = true)
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val exception = RuntimeException("Stream failed")
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", "gpt-4o", "api.groq.com", "-1")

                coEvery {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                        any(),
                    )
                } throws exception

                // When & Then
                val thrown =
                    assertThrows<RuntimeException> {
                        masaicCompletionService.createStreamingCompletion(request, headers, queryParams).collect {}
                    }
                assertEquals(exception.message, thrown.message)
                assertEquals(exception, thrown)
            }

        @Test
        fun `should throw IllegalArgumentException if Authorization header is missing`() =
            runTest {
                // Given
                val request = createDefaultRequest().copy(stream = true)
                val headers = createDefaultHeaders(auth = null) // No Auth header
                val queryParams = LinkedMultiValueMap<String, String>()

                // When & Then
                val thrown =
                    assertThrows<IllegalArgumentException> {
                        val flow = masaicCompletionService.createStreamingCompletion(request, headers, queryParams)
                        flow.collect { } // Need to consume the flow to trigger the validation
                    }
                assertEquals("api-key is missing.", thrown.message)
            }

        @Test
        fun `should extract model name correctly when using provider format for streaming`() =
            runTest {
                // Given
                val modelWithProvider = "groq@llama3-8b-8192"
                val expectedModelName = "llama3-8b-8192"
                val request = createDefaultRequest(model = modelWithProvider).copy(stream = true)
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedMetadata = InstrumentationMetadataInput("groq", expectedModelName, "api.groq.com", "-1")
                
                val streamEvent = ServerSentEvent.builder("data: {\"choices\":[{\"delta\":{\"content\":\"test\"}}]}").build()
                val expectedFlow = flowOf(streamEvent)

                coEvery {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                        any(),
                    )
                } returns expectedFlow

                // When
                val resultFlow = masaicCompletionService.createStreamingCompletion(request, headers, queryParams)
                val events = mutableListOf<ServerSentEvent<String>>()
                resultFlow.collect { events.add(it) }

                // Then
                assertEquals(1, events.size)
                assertEquals(streamEvent.data(), events[0].data())
                
                coVerify(exactly = 1) {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        match { params -> params.model().toString() == expectedModelName },
                        expectedMetadata,
                        any(),
                        any(),
                    )
                }
            }

        @Test
        fun `should extract model name correctly when using URL format for streaming`() =
            runTest {
                // Given
                val modelWithUrl = "http://localhost:11434/v1@local-model"
                val expectedModelName = "local-model"
                val request = createDefaultRequest(model = modelWithUrl).copy(stream = true)
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", "local-model", "localhost", "11434")
                
                val streamEvent = ServerSentEvent.builder("data: {\"choices\":[{\"delta\":{\"content\":\"local response\"}}]}").build()
                val expectedFlow = flowOf(streamEvent)

                coEvery {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                        any(),
                    )
                } returns expectedFlow

                // When
                val resultFlow = masaicCompletionService.createStreamingCompletion(request, headers, queryParams)
                val events = mutableListOf<ServerSentEvent<String>>()
                resultFlow.collect { events.add(it) }

                // Then
                assertEquals(1, events.size)
                assertEquals(streamEvent.data(), events[0].data())
                
                coVerify(exactly = 1) {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        match { params -> params.model().toString() == expectedModelName },
                        expectedMetadata,
                        any(),
                        any(),
                    )
                }
            }

        @Test
        fun `should handle empty stream gracefully`() =
            runTest {
                // Given
                val request = createDefaultRequest().copy(stream = true)
                val headers = createDefaultHeaders()
                val queryParams = LinkedMultiValueMap<String, String>()
                val expectedMetadata = InstrumentationMetadataInput("UNKNOWN", "gpt-4o", "api.groq.com", "-1")
                val emptyFlow = flowOf<ServerSentEvent<String>>()

                coEvery {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                        any(),
                    )
                } returns emptyFlow

                // When
                val resultFlow = masaicCompletionService.createStreamingCompletion(request, headers, queryParams)
                val events = mutableListOf<ServerSentEvent<String>>()
                resultFlow.collect { events.add(it) }

                // Then
                assertEquals(0, events.size)
                
                coVerify(exactly = 1) {
                    openAICompletionService.createCompletionStream(
                        any<OpenAIClient>(),
                        any<ChatCompletionCreateParams>(),
                        expectedMetadata,
                        any(),
                        any(),
                    )
                }
            }
    }

    @Nested
    inner class GetCompletionTests {
        @Test
        fun `should retrieve completion successfully`() =
            runTest {
                // Given
                val completionId = "cmpl-12345"
                val expectedCompletion = mockk<ChatCompletion>(relaxed = true)
                every { expectedCompletion.id() } returns completionId

                coEvery { completionStore.getCompletion(completionId) } returns expectedCompletion

                // When
                val result = masaicCompletionService.getCompletion(completionId)

                // Then
                assertEquals(expectedCompletion, result)
                coVerify(exactly = 1) { completionStore.getCompletion(completionId) }
            }

        @Test
        fun `should throw CompletionNotFoundException when completion not found`() =
            runTest {
                // Given
                val completionId = "cmpl-not-found"
                coEvery { completionStore.getCompletion(completionId) } returns null

                // When & Then
                val thrown =
                    assertThrows<CompletionNotFoundException> {
                        masaicCompletionService.getCompletion(completionId)
                    }
                assertEquals("Completion not found with ID: $completionId", thrown.message)
                coVerify(exactly = 1) { completionStore.getCompletion(completionId) }
            }

        @Test
        fun `should throw CompletionProcessingException on store error`() =
            runTest {
                // Given
                val completionId = "cmpl-error"
                val exception = RuntimeException("Store error")
                coEvery { completionStore.getCompletion(completionId) } throws exception

                // When & Then
                val thrown =
                    assertThrows<CompletionProcessingException> {
                        masaicCompletionService.getCompletion(completionId)
                    }
                assertEquals("Error retrieving completion: ${exception.message}", thrown.message)
                coVerify(exactly = 1) { completionStore.getCompletion(completionId) }
            }
    }

    @Nested
    inner class GetApiBaseUriTests {
        // Note: Testing environment variables might require additional setup (e.g., SystemLambda or mockkStatic)
        // These tests assume System.getenv behaves predictably in the test environment or is mocked.

        @Test
        fun `should return URL from model name when present`() {
            val headers = LinkedMultiValueMap<String, String>()
            val modelName = "https://custom.example.com/api/v1@my-model"
            val expectedUri = URI("https://custom.example.com/api/v1")

            val result = MasaicCompletionService.getApiBaseUri(headers, modelName)

            assertEquals(expectedUri, result)
        }

        @Test
        fun `should return provider URL from model name when present`() {
            val headers = LinkedMultiValueMap<String, String>()
            val testCases =
                mapOf(
                    "openai@gpt-4" to "https://api.openai.com/v1",
                    "groq@llama3" to "https://api.groq.com/openai/v1",
                    "anthropic@claude-3" to "https://api.anthropic.com/v1",
                    "google@gemini-pro" to "https://generativelanguage.googleapis.com/v1beta/openai/",
                    "ollama@mistral" to "http://localhost:11434/v1",
                )

            testCases.forEach { (modelName, expectedUrl) ->
                val result = MasaicCompletionService.getApiBaseUri(headers, modelName)
                assertEquals(URI(expectedUrl), result, "Failed for model: $modelName")
            }
        }

        @Test
        fun `should return provider URL from header if model name has no prefix`() {
            val headers = LinkedMultiValueMap<String, String>()
            headers.add("x-model-provider", "groq")
            val modelName = "llama3-8b-8192" // No prefix
            val expectedUri = URI("https://api.groq.com/openai/v1")

            val result = MasaicCompletionService.getApiBaseUri(headers, modelName)

            assertEquals(expectedUri, result)
        }

        @Test
        fun `should ignore header if model name has URL prefix`() {
            val headers = LinkedMultiValueMap<String, String>()
            headers.add("x-model-provider", "openai") // Should be ignored
            val modelName = "https://custom.example.com/api/v1@my-model"
            val expectedUri = URI("https://custom.example.com/api/v1")

            val result = MasaicCompletionService.getApiBaseUri(headers, modelName)

            assertEquals(expectedUri, result)
        }

        @Test
        fun `should ignore header if model name has provider prefix`() {
            val headers = LinkedMultiValueMap<String, String>()
            headers.add("x-model-provider", "openai") // Should be ignored
            val modelName = "groq@llama3"
            val expectedUri = URI("https://api.groq.com/openai/v1")

            val result = MasaicCompletionService.getApiBaseUri(headers, modelName)

            assertEquals(expectedUri, result)
        }

        @Test
        fun `should return default OpenAI URL if no prefix and no header`() {
            // Assuming OPENAI_BASE_URL env var is not set or mocked to null
            val headers = LinkedMultiValueMap<String, String>()
            val modelName = "gpt-4o"
            val expectedUri = URI(MasaicCompletionService.MODEL_DEFAULT_BASE_URL)
            
            val result = MasaicCompletionService.getApiBaseUri(headers, modelName)
            
            assertEquals(expectedUri, result)
        }

        @Test
        fun `should return default OpenAI URL for unrecognized provider in model name`() {
            val headers = LinkedMultiValueMap<String, String>()
            val modelName = "unknownProvider@some-model" 
            val expectedUri = URI(MasaicCompletionService.MODEL_DEFAULT_BASE_URL) // Falls back to default
            
            val result = MasaicCompletionService.getApiBaseUri(headers, modelName)
            
            assertEquals(expectedUri, result)
        }

        @Test
        fun `should return default OpenAI URL for unrecognized provider in header`() {
            val headers = LinkedMultiValueMap<String, String>()
            headers.add("x-model-provider", "unknownProvider")
            val modelName = "gpt-4o"
            val expectedUri = URI(MasaicCompletionService.MODEL_DEFAULT_BASE_URL) // Falls back to default
            
            val result = MasaicCompletionService.getApiBaseUri(headers, modelName)
            
            assertEquals(expectedUri, result)
        }

        // Add a test case for OPENAI_BASE_URL environment variable if possible to set/mock it
        // @Test
        // fun `should return URL from OPENAI_BASE_URL env var if no prefix/header`() { ... }
    }
} 
