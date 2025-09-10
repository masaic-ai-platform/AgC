import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionTool;
import java.util.List;

public class AgCLoopWithFileSearchExample {

  private static final String API_KEY = System.getenv("OPENAI_API_KEY");
  private static final String BASE_URL = "http://localhost:6644/v1";
  private static final String MODEL = "openai@gpt-4.1-mini";

  private final ObjectMapper objectMapper;

  public AgCLoopWithFileSearchExample() {
    this.objectMapper = new ObjectMapper();
  }

  public static void main(String[] args) {
    try {
      System.out.println("Starting AgC Loop with MCP Example...");

      // Initialize the OpenAI client with custom base URL for AgC
      OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(API_KEY).baseUrl(BASE_URL).build();

      // Create the example
      AgCLoopWithFileSearchExample example = new AgCLoopWithFileSearchExample();
      example.runSDKStreaming(client);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /** Demonstrates streaming using the OpenAI SDK (without tools for simplicity) */
  public void runSDKStreaming(OpenAIClient client) throws Exception {
    System.out.println("\n=== AgC SDK Streaming Chat Completion ===");
    // Create chat completion parameters with streaming enabled
    ChatCompletionCreateParams createParams =
        ChatCompletionCreateParams.builder()
            .addSystemMessage(
                "Use the given file search tool to prepare answer. Do not frame any answer yourself, If tool fails then just return sorry message with cause of sorry message.")
            .addUserMessage("Find one reaction of Magnesium.")
            .model(MODEL)
            .tools(
                List.of(
                    objectMapper.readValue(
                        String.format(fileSearchTool(), API_KEY), ChatCompletionTool.class)))
            .build();

    System.out.println("Sending streaming request to AgC API using OpenAI SDK...");
    System.out.println("Model: " + MODEL);
    System.out.println("Stream: true (via createStreaming method)");
    System.out.println("\n--- SDK Streaming Response ---");

    // Execute streaming chat completion
    try (StreamResponse<ChatCompletionChunk> streamResponse =
        client.chat().completions().createStreaming(createParams)) {
      streamResponse.stream().forEach(this::processSDKStreamChunk);
      System.out.println("\n--- SDK Streaming Complete ---");
    }
  }

  private String fileSearchTool() {
    return """
                {
                           "type": "function",
                           "function": {
                               "name": "file-search-tool",
                               "description": "This tool can make provide information about chemical reactions",
                               "strict": true,
                               "parameters": {
                                   "type": "object",
                                   "properties": {
                                       "type": {
                                           "type": "string",
                                           "enum": [
                                               "file_search"
                                           ]
                                       },
                                       "vector_store_ids": {
                                           "type": "array",
                                           "items": {
                                               "type": "string",
                                               "enum": [
                                                   "vs_68b98d857f73cf000000"
                                               ]
                                           }
                                       },
                                       "modelInfo": {
                                           "type": "object",
                                           "properties": {
                                               "bearerToken": {
                                                   "type": "string",
                                                   "enum": [
                                                       "%s"
                                                   ]
                                               },
                                               "model": {
                                                   "type": "string",
                                                   "enum": [
                                                       "openai@text-embedding-3-small"
                                                   ]
                                               }
                                           },
                                           "required": [
                                               "bearerToken",
                                               "model"
                                           ],
                                           "additionalProperties": false
                                       }
                                   },
                                   "required": [
                                       "type",
                                       "vector_store_ids",
                                       "modelInfo"
                                   ],
                                   "additionalProperties": false
                               }
                           }
                       }
                """;
  }

  /** Processes SDK stream chunks */
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
