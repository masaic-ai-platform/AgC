package ai.masaic.examples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.util.List;

public class AgCLoopWithMCPExample {

    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String BASE_URL = "http://localhost:6644/v1";
    private static final String MODEL = "openai@gpt-4.1-mini";

    private final ObjectMapper objectMapper;

    public AgCLoopWithMCPExample() {
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) {
        try {
            System.out.println("Starting AgC Loop with MCP Example...");

            // Initialize the OpenAI client with custom base URL for AgC
            OpenAIClient client = OpenAIOkHttpClient.builder()
                    .apiKey(API_KEY)
                    .baseUrl(BASE_URL)
                    .build();

            // Create the example
            AgCLoopWithMCPExample example = new AgCLoopWithMCPExample();
            example.runSDKStreaming(client);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Demonstrates streaming using the OpenAI SDK (without tools for simplicity)
     */
    public void runSDKStreaming(OpenAIClient client) throws Exception {
        System.out.println("\n=== AgC SDK Streaming Chat Completion ===");
        // Create chat completion parameters with streaming enabled
        ChatCompletionCreateParams createParams = ChatCompletionCreateParams.builder()
                .addSystemMessage("You are an ecommerce assistant who can help in product selection, add to cart, provide checkout link.\n" +
                        "1. Use all birds tool for product search and update cart.\n" +
                        "2. return image url in a proper markdown format.\n" +
                        "3. whenever you update cart, share checkout link also.")
                .addUserMessage("Find me sneakers of size 8")
                .model(MODEL)
                .tools(List.of(createMcpTool()))
                .build();

        System.out.println("Sending streaming request to AgC API using OpenAI SDK...");
        System.out.println("Model: " + MODEL);
        System.out.println("Stream: true (via createStreaming method)");
        System.out.println("\n--- SDK Streaming Response ---");

        // Execute streaming chat completion
        try (StreamResponse<ChatCompletionChunk> streamResponse = client.chat().completions().createStreaming(createParams)) {
            streamResponse.stream().forEach(this::processSDKStreamChunk);
            System.out.println("\n--- SDK Streaming Complete ---");
        }

    }

    private ChatCompletionTool createMcpTool() throws JsonProcessingException {
        return objectMapper.readValue(mcpToolJsonTemplate(), ChatCompletionTool.class);
//        return mcpTool.toBuilder().type(JsonValue.from("function")).build();

    }

    private String mcpToolJsonTemplate() {
        return """
                {
                            "type": "function",
                            "function": {
                                "name": "mcpTool-all-birds",
                                "parameters": {
                                    "type": "object",
                                    "properties": {
                                        "type": {
                                            "type": "string",
                                            "enum": [
                                                "mcp"
                                            ]
                                        },
                                        "server_label": {
                                            "type": "string",
                                            "enum": [
                                                "all-birds"
                                            ]
                                        },
                                        "server_url": {
                                            "type": "string",
                                            "enum": [
                                                "https://allbirds.com/api/mcp"
                                            ]
                                        },
                                        "allowed_tools": {
                                            "type": "array",
                                            "items": {
                                                "type": "string",
                                                "enum": [
                                                    "search_shop_catalog",
                                                    "get_cart",
                                                    "update_cart"
                                                ]
                                            }
                                        }
                                    },
                                    "required": [
                                        "type",
                                        "server_label",
                                        "server_url"
                                    ],
                                    "additionalProperties": false
                                }
                            }
                        }
                """;
    }

    /**
     * Processes SDK stream chunks
     */
    private void processSDKStreamChunk(ChatCompletionChunk chunk) {
        chunk.choices();
        if (!chunk.choices().isEmpty()) {
            var choice = chunk.choices().getFirst();

            // Display content delta
            if (choice.delta().content().isPresent()) {
                var content = choice.delta().content();
                if (content.isPresent() && !content.get().isEmpty()) {
                    System.out.print(content.get());
                }
            }
        }
    }
}
