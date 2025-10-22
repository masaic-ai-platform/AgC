package ai.masaic.openresponses.api.client

import ai.masaic.openresponses.api.model.InstrumentationMetadataInput
import ai.masaic.openresponses.api.support.service.TelemetryService
import ai.masaic.openresponses.tool.CompletionToolRequestContext
import ai.masaic.openresponses.tool.ToolService
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessage
import com.openai.models.chat.completions.ChatCompletionMessageToolCall
import io.micrometer.observation.Observation
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.stream.Stream
import kotlin.test.assertEquals

/**
 * Unit tests for MasaicOpenAiCompletionServiceImpl#create()
 */
@ExtendWith(MockKExtension::class)
class MasaicOpenAiCompletionServiceImplTest {
    private lateinit var client: OpenAIClient
    private lateinit var toolHandler: MasaicToolHandler
    private lateinit var completionStore: CompletionStore
    private lateinit var telemetryService: TelemetryService
    private lateinit var toolService: ToolService
    private lateinit var service: MasaicOpenAiCompletionServiceImpl

    // Mock spans and observations for telemetry
    private val mockObservation: Observation = mockk(relaxed = true)
    private val mockSpan: Span = mockk(relaxed = true)
    private val mockParentSpan: Span = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        client = mockk(relaxed = true)
        toolHandler = mockk(relaxed = true)
        completionStore = mockk(relaxed = true)
        telemetryService = mockk(relaxed = true)
        toolService = mockk(relaxed = true)
        
        // Default alias map empty
        every { toolService.buildAliasMap(any()) } returns emptyMap()

        // Setup default telemetry service behavior
        coEvery { telemetryService.withClientSpan(any(), any(), any(), any<suspend (Span) -> ChatCompletion>()) } coAnswers {
            val block = arg<suspend (Span) -> ChatCompletion>(3)
            block(mockSpan)
        }
        every { telemetryService.withChatCompletionTimer(any(), any(), any<() -> ChatCompletion>()) } answers {
            val block = arg<() -> ChatCompletion>(2)
            block()
        }
        coEvery { telemetryService.emitModelInputEventsForOtelSpan(any(), any(), any()) } returns Unit
        every { telemetryService.emitModelOutputEventsForOtel(any(), any(), any()) } returns Unit
        every { telemetryService.setChatCompletionObservationAttributesForOtelSpan(any(), any(), any(), any()) } returns Unit
        every { telemetryService.recordChatCompletionTokenUsage(any(), any(), any(), any(), any()) } returns Unit
        coEvery { telemetryService.startOtelSpan(any(), any(), any()) } returns mockSpan

        // Construct service under test
        service =
            spyk(
                MasaicOpenAiCompletionServiceImpl(
                    toolHandler,
                    completionStore,
                    telemetryService,
                    toolService,
                    mockk(), // objectMapper not exercised in these tests
                ),
                recordPrivateCalls = true,
            )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `create without tool calls and store false should return completion and not store`() =
        runBlocking {
            // Given: a ChatCompletion with STOP finish reason (no tool calls)
            val message =
                ChatCompletionMessage
                    .builder()
                    .role(JsonValue.from("assistant"))
                    .content("Hello")
                    .refusal(null)
                    .build()
            val choice =
                ChatCompletion.Choice
                    .builder()
                    .message(message)
                    .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                    .index(0)
                    .logprobs(null)
                    .build()
            val chatCompletion =
                ChatCompletion
                    .builder()
                    .id("test-id")
                    .created(123)
                    .model("gpt-model")
                    .choices(listOf(choice))
                    .build()
            
            // Mock client call to return the chat completion
            every { client.chat().completions().create(any<ChatCompletionCreateParams>()) } returns chatCompletion

            val params =
                ChatCompletionCreateParams
                    .builder()
                    .addUserMessage("Hello")
                    .model(ChatModel.GPT_3_5_TURBO)
                    .build()
            val metadata = InstrumentationMetadataInput()

            // When
            val result = service.create(client, params, metadata, mockParentSpan)

            // Then: returns the same completion and no storage
            assertEquals(chatCompletion, result)
            coVerify(exactly = 1) { telemetryService.withClientSpan(any(), any(), mockParentSpan, any<suspend (Span) -> ChatCompletion>()) }
            coVerify(exactly = 1) { telemetryService.emitModelInputEventsForOtelSpan(any(), params, metadata) }
            verify(exactly = 1) { telemetryService.emitModelOutputEventsForOtel(any(), chatCompletion, metadata) }
            verify(exactly = 1) { telemetryService.setChatCompletionObservationAttributesForOtelSpan(any(), chatCompletion, params, metadata) }
            coVerify(exactly = 0) { completionStore.storeCompletion(any(), any(), any()) }
        }

    @Test
    fun `create with store true should store completion`() =
        runBlocking {
            // Given: same completion but store flag set
            val message =
                ChatCompletionMessage
                    .builder()
                    .role(JsonValue.from("assistant"))
                    .content("World")
                    .refusal(null)
                    .build()
            val choice =
                ChatCompletion.Choice
                    .builder()
                    .message(message)
                    .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                    .index(0)
                    .logprobs(null)
                    .build()
            val chatCompletion =
                ChatCompletion
                    .builder()
                    .id("store-id")
                    .created(456)
                    .model("gpt-model")
                    .choices(listOf(choice))
                    .build()
            
            // Mock client call to return the chat completion
            every { client.chat().completions().create(any<ChatCompletionCreateParams>()) } returns chatCompletion

            val params =
                ChatCompletionCreateParams
                    .builder()
                    .addUserMessage("World")
                    .model(ChatModel.GPT_3_5_TURBO)
                    .store(true)
                    .build()
            val metadata = InstrumentationMetadataInput()

            coEvery {
                completionStore.storeCompletion(
                    chatCompletion,
                    params.messages(),
                    ofType(CompletionToolRequestContext::class),
                )
            } returns chatCompletion

            // When
            val result = service.create(client, params, metadata, mockParentSpan)

            // Then: returns and stores once
            assertEquals(chatCompletion, result)
            coVerify(exactly = 1) { telemetryService.withClientSpan(any(), any(), mockParentSpan, any<suspend (Span) -> ChatCompletion>()) }
            coVerify(exactly = 1) { telemetryService.emitModelInputEventsForOtelSpan(any(), params, metadata) }
            verify(exactly = 1) { telemetryService.emitModelOutputEventsForOtel(any(), chatCompletion, metadata) }
            verify(exactly = 1) { telemetryService.setChatCompletionObservationAttributesForOtelSpan(any(), chatCompletion, params, metadata) }
            coVerify(exactly = 1) {
                completionStore.storeCompletion(
                    chatCompletion,
                    params.messages(),
                    ofType(CompletionToolRequestContext::class),
                )
            }
        }

    @Test
    fun `create with non-native tool calls returns original completion without storing`() =
        runBlocking {
            // Given: ChatCompletion requests tool calls
            val toolCall =
                ChatCompletionMessageToolCall
                    .builder()
                    .id("tool-id")
                    .function(
                        ChatCompletionMessageToolCall.Function
                            .builder()
                            .name("unknownTool")
                            .arguments("{}")
                            .build(),
                    ).build()
            // Build assistant message containing tool call
            val assistantMsg =
                ChatCompletionMessage
                    .builder()
                    .role(JsonValue.from("assistant"))
                    .toolCalls(listOf(toolCall))
                    .content("")
                    .refusal(null)
                    .build()
            val choice =
                ChatCompletion.Choice
                    .builder()
                    .message(assistantMsg)
                    .finishReason(ChatCompletion.Choice.FinishReason.TOOL_CALLS)
                    .index(0)
                    .logprobs(null)
                    .build()
            val chatCompletion =
                ChatCompletion
                    .builder()
                    .id("tool-completion-id")
                    .created(1000)
                    .model("gpt-model")
                    .choices(listOf(choice))
                    .build()

            // Mock client call to return the chat completion
            every { client.chat().completions().create(any<ChatCompletionCreateParams>()) } returns chatCompletion
            
            // Stub handler to indicate unresolved client tools
            coEvery {
                toolHandler.handleCompletionToolCall(
                    chatCompletion,
                    any(),
                    client,
                    mockParentSpan,
                )
            } returns
                CompletionToolCallOutcome.Continue(
                    updatedMessages = emptyList(),
                    hasUnresolvedClientTools = true,
                )

            val params =
                ChatCompletionCreateParams
                    .builder()
                    .addUserMessage("Hi")
                    .model(ChatModel.GPT_3_5_TURBO)
                    .build()
            val metadata = InstrumentationMetadataInput()

            // When
            val result = service.create(client, params, metadata, mockParentSpan)

            // Then: original completion returned, no store
            assertEquals(chatCompletion, result)
            coVerify(exactly = 1) { telemetryService.withClientSpan(any(), any(), mockParentSpan, any<suspend (Span) -> ChatCompletion>()) }
            coVerify(exactly = 0) { completionStore.storeCompletion(any(), any(), any()) }
        }

    @Test
    fun `create with non-native tool calls and store true stores original completion`() =
        runBlocking {
            // Given: same as above but store=true
            val toolCall =
                ChatCompletionMessageToolCall
                    .builder()
                    .id("tool-id")
                    .function(
                        ChatCompletionMessageToolCall.Function
                            .builder()
                            .name("unknownTool")
                            .arguments("{}")
                            .build(),
                    ).build()
            val assistantMsg =
                ChatCompletionMessage
                    .builder()
                    .role(JsonValue.from("assistant"))
                    .toolCalls(listOf(toolCall))
                    .refusal(null)
                    .content("")
                    .build()
            val choice =
                ChatCompletion.Choice
                    .builder()
                    .message(assistantMsg)
                    .finishReason(ChatCompletion.Choice.FinishReason.TOOL_CALLS)
                    .index(0)
                    .logprobs(null)
                    .build()
            val chatCompletion =
                ChatCompletion
                    .builder()
                    .id("tool-store-id")
                    .created(2000)
                    .model("gpt-model")
                    .choices(listOf(choice))
                    .build()
            
            // Mock client call to return the chat completion
            every { client.chat().completions().create(any<ChatCompletionCreateParams>()) } returns chatCompletion
            
            coEvery {
                toolHandler.handleCompletionToolCall(chatCompletion, any(), client, mockParentSpan)
            } returns
                CompletionToolCallOutcome.Continue(
                    updatedMessages = emptyList(),
                    hasUnresolvedClientTools = true,
                )
            coEvery {
                completionStore.storeCompletion(chatCompletion, any(), ofType(CompletionToolRequestContext::class))
            } returns chatCompletion

            val params =
                ChatCompletionCreateParams
                    .builder()
                    .addUserMessage("Hi")
                    .model(ChatModel.GPT_3_5_TURBO)
                    .store(true)
                    .build()
            val metadata = InstrumentationMetadataInput()

            // When
            val result = service.create(client, params, metadata, mockParentSpan)

            // Then: original returned and stored once
            assertEquals(chatCompletion, result)
            coVerify(exactly = 1) { telemetryService.withClientSpan(any(), any(), mockParentSpan, any<suspend (Span) -> ChatCompletion>()) }
            coVerify(atLeast = 1) {
                completionStore.storeCompletion(chatCompletion, any(), ofType(CompletionToolRequestContext::class))
            }
        }

    @Test
    fun `createCompletionStream should invoke telemetry and return empty flow`() =
        runBlocking {
            // Stub streaming call to return no chunks
            val fakeStream =
                object : com.openai.core.http.StreamResponse<ChatCompletionChunk> {
                    override fun stream(): Stream<ChatCompletionChunk> = emptyList<ChatCompletionChunk>().stream()

                    override fun close() {}
                }
            every { client.chat().completions().createStreaming(any<ChatCompletionCreateParams>()) } returns fakeStream

            val params =
                ChatCompletionCreateParams
                    .builder()
                    .addUserMessage("Hello")
                    .model(ChatModel.GPT_3_5_TURBO)
                    .build()
            val metadata = InstrumentationMetadataInput()
            var finalResponseCalled = false
            val onFinalResponse: (ChatCompletion) -> Unit = { finalResponseCalled = true }

            // When
            val events = service.createCompletionStream(client, params, metadata, mockParentSpan, onFinalResponse).toList()

            // Then
            assertEquals(0, events.size)
            coVerify(exactly = 1) { telemetryService.startOtelSpan(any(), any(), mockParentSpan) }
            coVerify(exactly = 1) { telemetryService.emitModelInputEventsForOtelSpan(any(), params, metadata) }
        }
}
