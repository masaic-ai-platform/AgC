package usecases;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.Headers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.Tool;
import common.Util;

import java.util.List;

/**
 * Execute telco revenue retention process
 */
public class AgCLoopWithTelcoRevenueRetentionExample {
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String BASE_URL = "http://localhost:6644/v1";
    private static final String MODEL = "openai@gpt-4.1-mini";
    private static final String B64 = System.getenv("AGC_CREDS");
    //Default user-query this can be changed by user
    private static final String USER_QUERY = "Find high-value customers with repeated payment failures and unresolved complaints, and apply the action assign_agent to all customers.";
    private final ObjectMapper objectMapper;

    public AgCLoopWithTelcoRevenueRetentionExample() {
        this.objectMapper = new ObjectMapper()
                .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
                .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
    }

    public static void main(String[] args) {
        try {
            System.out.println("Starting AgC Loop with cohort selection usecase...");
            // Initialize credentials first
            Util.decrypt(B64);
            // Initialize the OpenAI client with custom base URL for AgC
            OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(API_KEY).baseUrl(BASE_URL)
                    .headers(Headers.builder().put("x-user-id", Util.getCreds("userId")).build())
                    .build();
            // Create the example
            AgCLoopWithTelcoRevenueRetentionExample example = new AgCLoopWithTelcoRevenueRetentionExample();
            example.runSDKStreaming(client);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void runSDKStreaming(OpenAIClient client) throws Exception {
        System.out.println("\n=== AgC SDK Streaming with Response api ===");
        Tool cohortSelectionTool = objectMapper.readValue(cohortSelectionTool(), Tool.class);
        Tool applyRetentionTool = objectMapper.readValue(applyRetentionActionTool(), Tool.class);
        ResponseCreateParams params = ResponseCreateParams.builder()
                .instructions(getSystemPrompt())
                .model(MODEL)
                .input(USER_QUERY)
                .tools(List.of(cohortSelectionTool, applyRetentionTool))
                .build();
        Response resp = client.responses().create(params);
        System.out.println((resp != null && resp.output() != null && !resp.output().isEmpty()) ? resp.output().get(0)._json() : "");
    }

    private String cohortSelectionTool() {
        return """
                {
                        "type": "function",
                        "name": "select_retention_cohort",
                        "description": "Returns a redacted list of customers who meet specified retention-risk criteria (revenue threshold, recent payment failures, unresolved complaints",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "min_revenue": {
                              "type": "number",
                              "default": 100000
                            },
                            "region": {
                              "type": "string",
                              "default": "IN"
                            },
                            "lookback_days": {
                              "type": "number",
                              "default": 1
                            },
                            "min_payment_failures": {
                              "type": "number",
                              "default": 1
                            },
                            "min_open_complaints": {
                              "type": "number",
                              "default": 2
                            },
                            "execution_specs": {
                              "type": "object",
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "client_side"
                                  ]
                                },
                                "maxRetryAttempts": {
                                  "type": "number",
                                  "enum": [
                                    1
                                  ]
                                },
                                "waitTimeInMillis": {
                                  "type": "number",
                                  "enum": [
                                    60000
                                  ]
                                }
                              }
                            }
                          },
                          "required": [
                            "min_revenue",
                            "region",
                            "lookback_days",
                            "min_payment_failures",
                            "min_open_complaints"
                          ],
                          "additionalProperties": false
                        },
                        "strict": true
                      }
                """;
    }

    private String applyRetentionActionTool() {
        return """
                 {
                        "type": "function",
                        "name": "apply_retention_actions",
                        "description": "Executes a single retention action for a batch of customers identified only by customer_id_hash.",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "action": {
                              "type": "string",
                              "enum": [
                                "offer_discount",
                                "assign_agent",
                                "schedule_callback",
                                "waive_fee"
                              ]
                            },
                            "customers": {
                              "type": "array",
                              "items": {
                                "type": "object",
                                "properties": {
                                  "customer_id_hash": {
                                    "type": "string"
                                  }
                                }
                              }
                            },
                            "execution_specs": {
                              "type": "object",
                              "properties": {
                                "type": {
                                  "type": "string",
                                  "enum": [
                                    "client_side"
                                  ]
                                },
                                "maxRetryAttempts": {
                                  "type": "number",
                                  "enum": [
                                    1
                                  ]
                                },
                                "waitTimeInMillis": {
                                  "type": "number",
                                  "enum": [
                                    60000
                                  ]
                                }
                              }
                            }
                          },
                          "required": [
                            "action",
                            "customers"
                          ],
                          "additionalProperties": false
                        },
                        "strict": true
                      }
                """;
    }

    private String getSystemPrompt(){
        return """
                ROLE
                You are Customer-Retention Assistant, a concise decision-maker that orchestrates two tools to analyze customer churn risk and trigger retention workflows.
                
                Available tools:
                1. select_retention_cohort — Returns a redacted list of customers who meet retention-risk criteria (e.g., revenue threshold, payment failures, unresolved complaints).
                2. apply_retention_actions — Executes one retention action for a batch of customers identified only by customer_id_hash.
                
                Core Logic and Rules:
                1. Validate inputs\s
                   - Always validate parameters before calling tools.
                   - If inputs are missing, execute with default parameters  (do not ask user) and clearly inform that defaults were used.
                2. Enforce order\s
                    - Always call select_retention_cohort first.
                    - Only call apply_retention_actions after a valid cohort   is returned.
                3. Single action\s
                    – Each call to apply_retention_actions must specify exactly one action and use the customer list from the previous step.
                4. Response Style
                    - Be factual, crisp, and conversational.
                    - For informational queries, answer directly without invoking any tool.
                   - After selecting the cohort, summarize findings as:
                       “AgC identifies <count> high-value customers at churn risk. Which action would you like to perform next?"
                5. Error handling\s
                    - If required parameters are invalid or malformed, reply with a clear and specific explanation of what’s missing or incorrect.
                    - If a tool returns no customers, respond with:
                      “No customers match the current retention-risk criteria.”.
                """.trim();
    }
}
