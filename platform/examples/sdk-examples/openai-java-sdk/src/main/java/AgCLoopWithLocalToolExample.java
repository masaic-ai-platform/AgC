import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import java.util.List;

public class AgCLoopWithLocalToolExample {

  private static final String API_KEY = System.getenv("OPENAI_API_KEY");
  private static final String E2B_API_KEY = System.getenv("E2B_API_KEY");
  private static final String BASE_URL = "http://localhost:6644/v1";
  private static final String MODEL = "claude@claude-3-7-sonnet-20250219";

  private final ObjectMapper objectMapper;

  public AgCLoopWithLocalToolExample() {
    this.objectMapper = new ObjectMapper();
  }

  public static void main(String[] args) {
    try {
      System.out.println("Starting AgC Loop with MCP And One local tool Example...");

      // Initialize the OpenAI client with custom base URL for AgC
      OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(API_KEY).baseUrl(BASE_URL).build();

      // Create the example
      AgCLoopWithLocalToolExample example = new AgCLoopWithLocalToolExample();
      example.runSDKStreaming(client);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /** Demonstrates streaming using the OpenAI SDK (without tools for simplicity) */
  public void runSDKStreaming(OpenAIClient client) throws Exception {
    System.out.println("\n=== AgC SDK Streaming Chat Completion ===");
    // Prepare initial messages
    String systemPrompt =
        """
                You are discount calculator agent. Given the original price and discount percentage, you calculate discounted price using provided tool.
                You have access to the following special tools:
                1. offer percentage provider. This provides applicable %age of discount on an item.
                2. discount calculatorr. This can calculate the final price based on the discount provided.
                """;
    String userPrompt = "Offer $100 sneakers to premium customers.";

    List<ChatCompletionMessageParam> messages = new java.util.ArrayList<>();
    messages.add(
        ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(systemPrompt).build()));
    messages.add(
        ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam.builder().content(userPrompt).build()));

    List<ChatCompletionTool> tools =
        List.of(
            objectMapper.readValue(String.format(pyFunTool, E2B_API_KEY), ChatCompletionTool.class),
            objectMapper.readValue(getDiscountPercentageTools, ChatCompletionTool.class));

    System.out.println("Sending streaming request to AgC API using OpenAI SDK...");
    System.out.println("Model: " + MODEL);
    System.out.println("Stream: true (via createStreaming method)");
    System.out.println("\n--- SDK Streaming Response ---");

    boolean done = false;
    while (!done) {
      ChatCompletionCreateParams createParams =
          ChatCompletionCreateParams.builder().model(MODEL).messages(messages).tools(tools).build();

      // Variables to collect tool call and content from this turn
      final boolean[] sawToolCall = {false};
      final boolean[] sawContent = {false};
      final String[] toolCallId = {null};
      final String[] toolName = {null};
      StringBuilder toolArgsBuilder = new StringBuilder();

      try (StreamResponse<ChatCompletionChunk> streamResponse =
          client.chat().completions().createStreaming(createParams)) {
        streamResponse.stream()
            .forEach(
                chunk -> {
                  if (!chunk.choices().isEmpty()) {
                    var choice = chunk.choices().getFirst();

                    // If model streams final content, print and mark done
                    if (choice.delta().content().isPresent()) {
                      var content = choice.delta().content().get();
                      if (!content.isEmpty()) {
                        System.out.print(content);
                        sawContent[0] = true;
                      }
                    }

                    // Collect tool call deltas
                    if (choice.delta().toolCalls().isPresent()) {
                      for (var toolCallDelta : choice.delta().toolCalls().get()) {
                        sawToolCall[0] = true;
                        if (toolCallDelta.id().isPresent()) {
                          toolCallId[0] = toolCallDelta.id().get();
                        }
                        if (toolCallDelta.function().isPresent()) {
                          var function = toolCallDelta.function().get();
                          if (function.name().isPresent()) {
                            toolName[0] = function.name().get();
                          }
                          if (function.arguments().isPresent()) {
                            toolArgsBuilder.append(function.arguments().get());
                          }
                        }
                      }
                    }
                  }
                });
      }

      // If a tool call was detected, synthesize next-turn messages and continue
      if (sawToolCall[0]) {
        if (toolCallId[0] == null) toolCallId[0] = "tool_call_1";
        if (toolName[0] == null) toolName[0] = "unknown_tool";
        String toolArgs = toolArgsBuilder.toString();

        ChatCompletionMessageToolCall.Function fn =
            ChatCompletionMessageToolCall.Function.builder()
                .name(toolName[0])
                .arguments(toolArgs)
                .build();

        ChatCompletionMessageToolCall toolCallMsg =
            ChatCompletionMessageToolCall.builder().id(toolCallId[0]).function(fn).build();

        // Assistant message containing the tool call
        ChatCompletionAssistantMessageParam assistantToolCallParam =
            ChatCompletionAssistantMessageParam.builder().addToolCall(toolCallMsg).build();
        messages.add(ChatCompletionMessageParam.ofAssistant(assistantToolCallParam));

        // If the local tool, execute and add tool result
        if ("get_discount_percentage".equals(toolName[0])) {
          String result = offer(toolArgs);
          ChatCompletionToolMessageParam toolResult =
              ChatCompletionToolMessageParam.builder()
                  .toolCallId(toolCallId[0])
                  .content(result)
                  .build();
          messages.add(ChatCompletionMessageParam.ofTool(toolResult));
        }

        // Start next turn
        continue;
      }

      // If we received content (final answer), we're done
      if (sawContent[0]) {
        done = true;
      } else {
        // Neither tool call nor content – end to avoid infinite loop
        done = true;
      }
    }
  }

  private String offer(String customerType) {
    System.out.println("[LocalTool]: Calculating discount for " + customerType);
    String discount = "This customer is eligible for 5% discount";
    System.out.println("[LocalTool]: " + discount);
    return discount;
  }

  private String pyFunTool =
      """
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

  private final String getDiscountPercentageTools =
      """
                    {
                              "type": "function",
                              "function": {
                                "name": "get_discount_percentage",
                                "description": "Return discount percentage based on input customer type",
                                "strict": true,
                                "parameters": {
                                  "type": "object",
                                  "properties": {
                                    "customer_type": {
                                      "type": "string",
                                      "description": "Type of the customer, such as 'regular', 'premium', or 'new'"
                                    }
                                  },
                                  "required": [
                                    "customer_type"
                                  ],
                                  "additionalProperties": false
                                }
                              }
                            }
            """;
}
