package ai.masaic.openresponses.api.service

import ai.masaic.openresponses.api.client.ResponseStore
import ai.masaic.openresponses.api.model.InputMessageItem
import ai.masaic.openresponses.api.utils.PayloadFormatter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.models.responses.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResponseStoreServiceTest {
    private lateinit var objectMapper: ObjectMapper
    private lateinit var responseStore: ResponseStore
    private lateinit var payloadFormatter: PayloadFormatter
    private lateinit var responseStoreService: ResponseStoreService

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

        responseStoreService = ResponseStoreService(responseStore = responseStore, payloadFormatter = payloadFormatter)
    }

    @Test
    fun `listInputItems should retrieve input items from ResponseStore`() {
        // Given
        val responseId = "resp_123456"
        val mockResponse = mockk<Response>()

        // Create actual ResponseInputItem objects instead of mocks
        val inputItems =
            listOf(
                objectMapper.convertValue(
                    ResponseInputItem.ofFunctionCall(
                        ResponseFunctionToolCall
                            .builder()
                            .id("fc_1")
                            .name("test_function")
                            .arguments("{}")
                            .callId("fc_1")
                            .build(),
                    ),
                    InputMessageItem::class.java,
                ),
                objectMapper.convertValue(
                    ResponseInputItem.ofFunctionCallOutput(
                        ResponseInputItem.FunctionCallOutput
                            .builder()
                            .callId("fc_1")
                            .output("{\"result\": \"success\"}")
                            .build(),
                    ),
                    InputMessageItem::class.java,
                ),
                objectMapper.convertValue(
                    ResponseInputItem.ofMessage(
                        ResponseInputItem.Message
                            .builder()
                            .role(ResponseInputItem.Message.Role.USER)
                            .content(
                                listOf(
                                    ResponseInputContent.ofInputText(
                                        ResponseInputText
                                            .builder()
                                            .text("Hello")
                                            .build(),
                                    ),
                                ),
                            ).build(),
                    ),
                    InputMessageItem::class.java,
                ),
            )

        coEvery { responseStore.getResponse(responseId) } returns mockResponse
        coEvery { responseStore.getInputItems(responseId) } returns inputItems

        // When
        val result = runBlocking { responseStoreService.listInputItems(responseId, 2, "desc", null, null) }

        // Then
        assertEquals(2, result.data.size, "Should return limited input items")
        coVerify(exactly = 1) { responseStore.getResponse(responseId) }
        coVerify(exactly = 1) { responseStore.getInputItems(responseId) }
    }

    @Test
    fun `listInputItems should throw ResponseNotFoundException if response not found`() {
        // Given
        val responseId = "nonexistent_resp"
        coEvery { responseStore.getResponse(responseId) } returns null

        // When & Then
        assertThrows(ResponseNotFoundException::class.java) {
            runBlocking {
                responseStoreService.listInputItems(responseId, 10, "desc", null, null)
            }
        }

        coVerify(exactly = 1) { responseStore.getResponse(responseId) }
        coVerify(exactly = 0) { responseStore.getInputItems(any()) }
    }
}
