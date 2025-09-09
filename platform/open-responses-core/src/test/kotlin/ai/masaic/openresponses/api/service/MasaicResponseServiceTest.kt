package ai.masaic.openresponses.api.service

import ai.masaic.openresponses.api.client.MasaicOpenAiResponseServiceImpl
import ai.masaic.openresponses.api.client.MasaicParameterConverter
import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.model.InstrumentationMetadataInput
import ai.masaic.openresponses.api.support.service.TelemetryService
import ai.masaic.openresponses.api.utils.PayloadFormatter
import ai.masaic.openresponses.tool.ToolService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.client.OpenAIClient
import com.openai.models.ResponsesModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseTextConfig
import com.openai.models.responses.ToolChoiceOptions
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.mockk.*
import io.opentelemetry.api.OpenTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.codec.ServerSentEvent
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.util.Optional

@ExtendWith(SpringExtension::class)
class MasaicResponseServiceTest {
    private lateinit var toolService: ToolService
    private lateinit var openAIResponseService: MasaicOpenAiResponseServiceImpl
    private lateinit var masaicResponseService: MasaicResponseService
    private lateinit var payloadFormatter: PayloadFormatter
    private lateinit var responseStore: ResponseStore
    private lateinit var objectMapper: ObjectMapper
    private lateinit var parameterConverter: MasaicParameterConverter
    private val telemetryService: TelemetryService = TelemetryServiceForTests(ObservationRegistry.NOOP, OpenTelemetry.noop(), SimpleMeterRegistry())
    private val completionParams: ChatCompletionCreateParams = mockk()

    @BeforeEach
    fun setup() {
        responseStore = mockk()
        objectMapper = jacksonObjectMapper()
        payloadFormatter =
            mockk {
                every { formatResponse(any()) } answers {
                    objectMapper.valueToTree<JsonNode>(firstArg()) as ObjectNode
                }
            }
        toolService = mockk()
        openAIResponseService = mockk()
        parameterConverter = mockk()
        masaicResponseService = MasaicResponseService(openAIResponseService, responseStore, payloadFormatter, objectMapper, telemetryService, parameterConverter)
    }

    @Test
    fun `createResponse should call openAIResponseService and return a Response`() =
        runBlocking {
            // Given
            val request =
                mockk<ResponseCreateParams.Body> {
                    every { previousResponseId() } returns Optional.empty()
                    every { input() } returns ResponseCreateParams.Input.ofText("Test")
                    every { model() } returns ResponsesModel.ofString("gpt-4")
                    every { instructions() } returns Optional.empty()
                    every { reasoning() } returns Optional.empty()
                    every { parallelToolCalls() } returns Optional.of(true)
                    every { maxOutputTokens() } returns Optional.of(256)
                    every { include() } returns Optional.empty()
                    every { metadata() } returns Optional.empty()
                    every { store() } returns Optional.of(true)
                    every { temperature() } returns Optional.of(0.7)
                    every { topP() } returns Optional.of(0.9)
                    every { truncation() } returns Optional.empty()
                    every { _additionalProperties() } returns emptyMap()

                    // For the optional fields that return java.util.Optional<T>:
                    every { text() } returns Optional.of(ResponseTextConfig.builder().build())
                    every { user() } returns Optional.of("someUser")
                    every { toolChoice() } returns Optional.of(ResponseCreateParams.ToolChoice.ofOptions(ToolChoiceOptions.AUTO))
                    every { tools() } returns Optional.of(listOf())
                }
            val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
            headers.add("Authorization", "Bearer testKey")
            val queryParams: MultiValueMap<String, String> = LinkedMultiValueMap()

            val expectedResponse = mockk<Response>()
            coEvery { openAIResponseService.create(ofType<OpenAIClient>(), any(), any(), any()) } returns expectedResponse

            every { runBlocking { parameterConverter.prepareCompletion(any()) } } returns completionParams

            // When
            val result = masaicResponseService.createResponse(request, headers, queryParams)

            // Then
            assertSame(expectedResponse, result, "Should return the mocked response")
        }

    @Test
    fun `createResponse should throw IllegalArgumentException if Authorization header is missing`() =
        runBlocking {
            // Given
            val request =
                mockk<ResponseCreateParams.Body> {
                    every { input() } returns ResponseCreateParams.Input.ofText("Test")
                    every { model() } returns ResponsesModel.ofString("gpt-4")
                    every { instructions() } returns Optional.empty()
                    every { reasoning() } returns Optional.empty()
                    every { parallelToolCalls() } returns Optional.of(true)
                    every { maxOutputTokens() } returns Optional.of(256)
                    every { include() } returns Optional.empty()
                    every { metadata() } returns Optional.empty()
                    every { store() } returns Optional.of(true)
                    every { temperature() } returns Optional.of(0.7)
                    every { topP() } returns Optional.of(0.9)
                    every { truncation() } returns Optional.empty()
                    every { _additionalProperties() } returns emptyMap()

                    // For the optional fields that return java.util.Optional<T>:
                    every { text() } returns Optional.of(ResponseTextConfig.builder().build())
                    every { user() } returns Optional.of("someUser")
                    every { toolChoice() } returns Optional.of(ResponseCreateParams.ToolChoice.ofOptions(ToolChoiceOptions.AUTO))
                    every { tools() } returns Optional.of(listOf())
                }
            val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
            val queryParams: MultiValueMap<String, String> = LinkedMultiValueMap()

            // When & Then
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    masaicResponseService.createResponse(request, headers, queryParams)
                }
            }
            Unit
        }

//    @Test
//    fun `createResponse should accept lowercase authorization header`() =
//        runBlocking {
//            // Given
//            val request =
//                mockk<ResponseCreateParams.Body> {
//                    every { previousResponseId() } returns Optional.empty()
//                    every { input() } returns ResponseCreateParams.Input.ofText("Test")
//                    every { model() } returns ResponsesModel.ofString("gpt-4")
//                    every { instructions() } returns Optional.empty()
//                    every { reasoning() } returns Optional.empty()
//                    every { parallelToolCalls() } returns Optional.of(true)
//                    every { maxOutputTokens() } returns Optional.of(256)
//                    every { include() } returns Optional.empty()
//                    every { metadata() } returns Optional.empty()
//                    every { store() } returns Optional.of(true)
//                    every { temperature() } returns Optional.of(0.7)
//                    every { topP() } returns Optional.of(0.9)
//                    every { truncation() } returns Optional.empty()
//                    every { _additionalProperties() } returns emptyMap()
//
//                    every { text() } returns Optional.of(ResponseTextConfig.builder().build())
//                    every { user() } returns Optional.of("someUser")
//                    every { toolChoice() } returns Optional.of(ResponseCreateParams.ToolChoice.ofOptions(ToolChoiceOptions.AUTO))
//                    every { tools() } returns Optional.of(listOf())
//                }
//            val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
//            headers.add("authorization", "Bearer testKey")
//            val queryParams: MultiValueMap<String, String> = LinkedMultiValueMap()
//
//            val expectedResponse = mockk<Response>()
//            coEvery { openAIResponseService.create(ofType<OpenAIClient>(), any(), any(), any()) } returns expectedResponse
//
//            // When
//            val result = masaicResponseService.createResponse(request, headers, queryParams)
//
//            // Then
//            assertSame(expectedResponse, result)
//        }

    @Test
    fun `createStreamingResponse should return a Flow of ServerSentEvent`() =
        runBlocking {
            // Given
            val request =
                mockk<ResponseCreateParams.Body> {
                    every { previousResponseId() } returns Optional.empty()
                    every { input() } returns ResponseCreateParams.Input.ofText("Test")
                    every { model() } returns ResponsesModel.ofString("gpt-4")
                    every { instructions() } returns Optional.empty()
                    every { reasoning() } returns Optional.empty()
                    every { parallelToolCalls() } returns Optional.of(true)
                    every { maxOutputTokens() } returns Optional.of(256)
                    every { include() } returns Optional.empty()
                    every { metadata() } returns Optional.empty()
                    every { store() } returns Optional.of(true)
                    every { temperature() } returns Optional.of(0.7)
                    every { topP() } returns Optional.of(0.9)
                    every { truncation() } returns Optional.empty()
                    every { _additionalProperties() } returns emptyMap()

                    // For the optional fields that return java.util.Optional<T>:
                    every { text() } returns Optional.of(ResponseTextConfig.builder().build())
                    every { user() } returns Optional.of("someUser")
                    every { toolChoice() } returns Optional.of(ResponseCreateParams.ToolChoice.ofOptions(ToolChoiceOptions.AUTO))
                    every { tools() } returns Optional.of(listOf())
                }
            val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
            headers.add("Authorization", "Bearer testKey")
            val queryParams: MultiValueMap<String, String> = LinkedMultiValueMap()

            val expectedFlow: Flow<ServerSentEvent<String>> =
                flowOf(
                    ServerSentEvent.builder("data1").build(),
                    ServerSentEvent.builder("data2").build(),
                )

            val actualMetadata =
                InstrumentationMetadataInput(
                    genAISystem = "UNKNOWN",
                    modelName = "gpt-4o",
                    modelProviderAddress = "api.groq.com",
                    modelProviderPort = "-1",
                )
            coEvery {
                openAIResponseService.createCompletionStream(
                    any(),
                    any(),
                    any(),
                )
            } returns expectedFlow

            // When
            val resultFlow = masaicResponseService.createStreamingResponse(request, headers, queryParams)
            val collectedEvents = resultFlow.toList()

            // Then
            assertEquals(2, collectedEvents.size)
            assertEquals("data1", collectedEvents[0].data())
            assertEquals("data2", collectedEvents[1].data())

            coVerify(exactly = 1) {
                openAIResponseService.createCompletionStream(
                    any(),
                    any(),
                    any(),
                )
            }
            confirmVerified(openAIResponseService)
        }

    @Test
    fun `createStreamingResponse should throw IllegalArgumentException if Authorization header is missing`() {
        // Given
        val request = mockk<ResponseCreateParams.Body>()
        val headers: MultiValueMap<String, String> = LinkedMultiValueMap()
        val queryParams: MultiValueMap<String, String> = LinkedMultiValueMap()

        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                masaicResponseService.createStreamingResponse(request, headers, queryParams)
            }
        }
    }

    @Test
    fun `getApiBaseUri should handle full URL in model name`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()
        val modelName = "https://custom-api.example.com/v1@gpt-4"

        // When
        val result = MasaicResponseService.getApiBaseUri(headers, modelName)

        // Then
        assertEquals("https://custom-api.example.com/v1", result.toString())
    }

    @Test
    fun `getApiBaseUri should handle provider name in model`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()
        val testCases =
            mapOf(
                "openai@gpt-4" to "https://api.openai.com/v1",
                "claude@claude-3" to "https://api.anthropic.com/v1",
                "groq@mixtral-8x7b" to "https://api.groq.com/openai/v1",
                "anthropic@claude-3" to "https://api.anthropic.com/v1",
            )

        for ((modelName, expectedUrl) in testCases) {
            // When
            val result = MasaicResponseService.getApiBaseUri(headers, modelName)

            // Then
            assertEquals(expectedUrl, result.toString(), "Failed for model: $modelName")
        }
    }

    @Test
    fun `getApiBaseUri should handle unknown provider in model`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()
        val modelName = "unknown-provider@model-name"

        // When
        val result = MasaicResponseService.getApiBaseUri(headers, modelName)

        // Then
        assertEquals(MasaicResponseService.MODEL_DEFAULT_BASE_URL, result.toString())
    }

    @Test
    fun `getApiBaseUri should handle null model name`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()

        // When
        val result = MasaicResponseService.getApiBaseUri(headers, null)

        // Then
        assertEquals(MasaicResponseService.MODEL_DEFAULT_BASE_URL, result.toString())
    }

    @Test
    fun `getApiBaseUri should handle empty model prefix`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()
        val modelName = "@gpt-4"

        // When
        val result = MasaicResponseService.getApiBaseUri(headers, modelName)

        // Then
        assertEquals(MasaicResponseService.MODEL_DEFAULT_BASE_URL, result.toString())
    }

    @Test
    fun `getApiBaseUri should prioritize model prefix over x-model-provider header`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()
        headers.add("x-model-provider", "openai")
        val modelName = "claude@claude-3"

        // When
        val result = MasaicResponseService.getApiBaseUri(headers, modelName)

        // Then
        assertEquals("https://api.anthropic.com/v1", result.toString())
    }

    @Test
    fun `getDefaultApiUri should handle known providers in header`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()
        val testCases =
            mapOf(
                "openai" to "https://api.openai.com/v1",
                "claude" to "https://api.anthropic.com/v1",
                "groq" to "https://api.groq.com/openai/v1",
                "anthropic" to "https://api.anthropic.com/v1",
            )

        for ((provider, expectedUrl) in testCases) {
            // When
            headers.set("x-model-provider", provider)
            val result = MasaicResponseService.getDefaultApiUri(headers)

            // Then
            assertEquals(expectedUrl, result.toString(), "Failed for provider: $provider")
        }
    }

    @Test
    fun `getDefaultApiUri should handle unknown provider in header`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()
        headers.add("x-model-provider", "unknown-provider")

        // When
        val result = MasaicResponseService.getDefaultApiUri(headers)

        // Then
        assertEquals(MasaicResponseService.MODEL_DEFAULT_BASE_URL, result.toString())
    }

    @Test
    fun `getDefaultApiUri should handle missing x-model-provider header`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()

        // When
        val result = MasaicResponseService.getDefaultApiUri(headers)

        // Then
        assertEquals(MasaicResponseService.MODEL_DEFAULT_BASE_URL, result.toString())
    }

    @Test
    fun `getDefaultApiUri should handle case insensitive provider names`() {
        // Given
        val headers = LinkedMultiValueMap<String, String>()
        val testCases =
            listOf(
                "OPENAI",
                "OpenAI",
                "openAI",
                "CLAUDE",
                "Claude",
                "GROQ",
                "Groq",
            )

        for (provider in testCases) {
            // When
            headers.set("x-model-provider", provider)
            val result = MasaicResponseService.getDefaultApiUri(headers)

            // Then
            val expectedUrl =
                when (provider.lowercase()) {
                    "openai" -> "https://api.openai.com/v1"
                    "claude" -> "https://api.anthropic.com/v1"
                    "groq" -> "https://api.groq.com/openai/v1"
                    else -> MasaicResponseService.MODEL_DEFAULT_BASE_URL
                }
            assertEquals(expectedUrl, result.toString(), "Failed for provider: $provider")
        }
    }
}
