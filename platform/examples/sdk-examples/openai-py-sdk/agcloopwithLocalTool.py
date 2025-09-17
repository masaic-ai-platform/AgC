import os
import sys
import json
from typing import Dict, List, Any
from openai import OpenAI


class AgCLoopWithLocalToolExample:

    API_KEY = os.getenv("OPENAI_API_KEY")
    E2B_API_KEY = os.getenv("E2B_API_KEY")
    BASE_URL = "http://localhost:6644/v1"
    MODEL = "claude@claude-3-7-sonnet-20250219"

    def __init__(self):
        pass

    def main(self):
        try:
            print("Starting AgC Loop with MCP And One local tool Example...")

            # Initialize the OpenAI client with custom base URL for AgC
            client = OpenAI(
                api_key=self.API_KEY,
                base_url=self.BASE_URL
            )

            # Create the example
            example = AgCLoopWithLocalToolExample()
            example.runSDKStreaming(client)
        except Exception as ex:
            ex.print_exc()

    def runSDKStreaming(self, client):
        print("\n=== AgC SDK Streaming Chat Completion ===")
        # Prepare initial messages
        systemPrompt = """
                        You are discount calculator agent. Given the original price and discount percentage, you calculate discounted price using provided tool.
                        You have access to the following special tools:
                        1. offer percentage provider. This provides applicable %age of discount on an item.
                        2. discount calculatorr. This can calculate the final price based on the discount provided.
                        """
        userPrompt = "Offer $100 sneakers to premium customers."

        messages = [
            {"role": "system", "content": systemPrompt},
            {"role": "user", "content": userPrompt}
        ]

        tools = [
            self.create_py_fun_tool(),
            json.loads(self.getDiscountPercentageTools)
        ]

        print("Sending streaming request to AgC API using OpenAI SDK...")
        print("Model: " + self.MODEL)
        print("Stream: true (via createStreaming method)")
        print("\n--- SDK Streaming Response ---")

        done = False
        while not done:
            # Variables to collect tool call and content from this turn
            sawToolCall = False
            sawContent = False
            toolCallId = None
            toolName = None
            toolArgsBuilder = ""

            stream = client.chat.completions.create(
                model=self.MODEL,
                messages=messages,
                tools=tools,
                stream=True
            )

            for chunk in stream:
                if chunk.choices:
                    choice = chunk.choices[0]

                    # If model streams final content, print and mark done
                    if choice.delta and choice.delta.content:
                        content = choice.delta.content
                        if content:
                            print(content, end='', flush=True)
                            sawContent = True

                    # Collect tool call deltas
                    if choice.delta and choice.delta.tool_calls:
                        for toolCallDelta in choice.delta.tool_calls:
                            sawToolCall = True
                            if toolCallDelta.id:
                                toolCallId = toolCallDelta.id
                            if toolCallDelta.function:
                                if toolCallDelta.function.name:
                                    toolName = toolCallDelta.function.name
                                if toolCallDelta.function.arguments:
                                    toolArgsBuilder += toolCallDelta.function.arguments

            # If a tool call was detected, synthesize next-turn messages and continue
            if sawToolCall:
                if toolCallId is None:
                    toolCallId = "tool_call_1"
                if toolName is None:
                    toolName = "unknown_tool"
                toolArgs = toolArgsBuilder

                # Assistant message containing the tool call
                assistantMessage = {
                    "role": "assistant",
                    "tool_calls": [
                        {
                            "id": toolCallId,
                            "type": "function",
                            "function": {
                                "name": toolName,
                                "arguments": toolArgs
                            }
                        }
                    ]
                }
                messages.append(assistantMessage)

                # If the local tool, execute and add tool result
                if toolName == "get_discount_percentage":
                    result = self.offer(toolArgs)
                    toolResult = {
                        "role": "tool",
                        "tool_call_id": toolCallId,
                        "content": result
                    }
                    messages.append(toolResult)

                # Start next turn
                continue

            # If we received content (final answer), we're done
            if sawContent:
                done = True
            else:
                # Neither tool call nor content – end to avoid infinite loop
                done = True

    def offer(self, customerType):
        print(f"[LocalTool]: Calculating discount for {customerType}")
        discount = "This customer is eligible for 5% discount"
        print(f"[LocalTool]: {discount}")
        return discount

    def create_py_fun_tool(self) -> Dict[str, Any]:
        return {
            "type": "function",
            "function": {
                "name": "python-function-tool",
                "strict": True,
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
                                    "additionalProperties": False
                                }
                            },
                            "required": [
                                "name",
                                "description"
                            ],
                            "additionalProperties": False
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
                                        self.E2B_API_KEY
                                    ]
                                }
                            },
                            "required": [
                                "server_label",
                                "url",
                                "apiKey"
                            ],
                            "additionalProperties": False
                        }
                    },
                    "required": [
                        "type",
                        "code",
                        "code_interpreter",
                        "tool_def"
                    ],
                    "additionalProperties": False
                }
            }
        }

    getDiscountPercentageTools = """
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
    """


if __name__ == "__main__":
    example = AgCLoopWithLocalToolExample()
    example.main()
