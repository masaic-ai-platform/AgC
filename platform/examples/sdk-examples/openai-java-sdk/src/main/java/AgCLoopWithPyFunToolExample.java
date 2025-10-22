import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionTool;
import java.util.List;

public class AgCLoopWithPyFunToolExample {

  private static final String API_KEY = System.getenv("OPENAI_API_KEY");
  private static final String BASE_URL = "http://localhost:6644/v1";
  private static final String E2B_API_KEY = System.getenv("E2B_API_KEY");
  private static final String MODEL = "openai@gpt-4.1-mini";

  private final ObjectMapper objectMapper;

  public AgCLoopWithPyFunToolExample() {
    this.objectMapper = new ObjectMapper();
  }

  public static void main(String[] args) {
    try {
      System.out.println("Starting AgC Loop with MCP Example...");

      // Initialize the OpenAI client with custom base URL for AgC
      OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(API_KEY).baseUrl(BASE_URL).build();

      // Create the example
      AgCLoopWithPyFunToolExample example = new AgCLoopWithPyFunToolExample();
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
                "Use the given compute function tool to perform calculations. Do not perform calculations yourself. If tool fails then just return sorry message with cause of sorry message.")
            .addUserMessage("Give discount of 5% on $100.")
            .model(MODEL)
            .tools(
                List.of(
                    objectMapper.readValue(
                        String.format(pyFunTool(), E2B_API_KEY), ChatCompletionTool.class)))
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

  private String pyFunTool() {
    return """
                        {
                                  "type": "function",
                                  "function": {
                                    "name": "python-function-tool",
                                    "strict": true,
                                    "parameters": {
                                      "type": "object",
                                      "properties": {
                                        "type": {
                                          "type": "string",
                                          "enum": [
                                            "py_fun_tool"
                                          ]
                                        },
                                        "tool_def": {
                                          "type": "object",
                                          "properties": {
                                            "name": {
                                              "type": "string",
                                              "enum": [
                                                "discount_calculator_macro"
                                              ]
                                            },
                                            "description": {
                                              "type": "string",
                                              "enum": [
                                                "Applies a discount to a price and returns the calculation details as a dictionary."
                                              ]
                                            },
                                            "parameters": {
                                              "type": "object",
                                              "properties": {
                                                "price": {
                                                  "type": "number"
                                                },
                                                "discount_pct": {
                                                  "type": "number"
                                                }
                                              },
                                              "required": [
                                                "price",
                                                "discount_pct"
                                              ],
                                              "additionalProperties": false
                                            }
                                          },
                                          "required": [
                                            "name",
                                            "description"
                                          ],
                                          "additionalProperties": false
                                        },
                                        "code": {
                                          "type": "string",
                                          "enum": [
                                            "ZGVmIGRpc2NvdW50X2NhbGN1bGF0b3JfbWFjcm8ocHJpY2U6IGZsb2F0LCBkaXNjb3VudF9wY3Q6IGZsb2F0KSAtPiBkaWN0OgogICAgIiIiCiAgICBBcHBseSBhIGRpc2NvdW50IHRvIGEgc2luZ2xlIHByaWNlIGFuZCByZXR1cm4gcmVzdWx0IGFzIGFuIG9iamVjdC4KCiAgICBBcmdzOgogICAgICAgIHByaWNlIChmbG9hdCk6IE9yaWdpbmFsIHByaWNlCiAgICAgICAgZGlzY291bnRfcGN0IChmbG9hdCk6IERpc2NvdW50IHBlcmNlbnRhZ2UgKGUuZy4sIDIwIGZvciAyMCUpCgogICAgUmV0dXJuczoKICAgICAgICBkaWN0OiB7CiAgICAgICAgICAgICJvcmlnaW5hbF9wcmljZSI6IDxmbG9hdD4sCiAgICAgICAgICAgICJkaXNjb3VudF9wY3QiOiA8ZmxvYXQ+LAogICAgICAgICAgICAiZGlzY291bnRlZF9wcmljZSI6IDxmbG9hdD4KICAgICAgICB9CiAgICAiIiIKICAgIGRpc2NvdW50ZWRfcHJpY2UgPSBwcmljZSAqICgxIC0gZGlzY291bnRfcGN0IC8gMTAwLjApCiAgICByZXR1cm4gewogICAgICAgICJvcmlnaW5hbF9wcmljZSI6IHByaWNlLAogICAgICAgICJkaXNjb3VudF9wY3QiOiBkaXNjb3VudF9wY3QsCiAgICAgICAgImRpc2NvdW50ZWRfcHJpY2UiOiByb3VuZChkaXNjb3VudGVkX3ByaWNlLCAyKQogICAgfQ=="
                                          ]
                                        },
                                        "code_interpreter": {
                                          "type": "object",
                                          "properties": {
                                            "server_label": {
                                              "type": "string",
                                              "enum": [
                                                "e2b-server"
                                              ]
                                            },
                                            "url": {
                                              "type": "string",
                                              "enum": [
                                                "http://localhost:8000/mcp"
                                              ]
                                            },
                                            "apiKey": {
                                              "type": "string",
                                              "enum": [
                                                "%s"
                                              ]
                                            }
                                          },
                                          "required": [
                                            "server_label",
                                            "url",
                                            "apiKey"
                                          ],
                                          "additionalProperties": false
                                        }
                                      },
                                      "required": [
                                        "type",
                                        "code",
                                        "code_interpreter",
                                        "tool_def"
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
