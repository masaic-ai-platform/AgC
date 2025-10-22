import os
import sys
from typing import Dict, List, Any
from openai import OpenAI


class AgCLoopWithPyFunToolExample:
    # Configuration constants
    API_KEY = os.getenv("OPENAI_API_KEY", "")
    BASE_URL = "http://localhost:6644/v1"
    E2B_API_KEY = os.getenv("E2B_API_KEY", "")
    MODEL = "openai@gpt-4.1-mini"
    
    def __init__(self):
        """Initialize the AgC example with OpenAI client."""
        if not self.API_KEY:
            print("❌ Error: OPENAI_API_KEY environment variable not set")
            sys.exit(1)

        if not self.E2B_API_KEY:
            print("❌ Error: E2B_API_KEY environment variable not set")
            sys.exit(1)

        self.client = OpenAI(
            api_key=self.API_KEY,
            base_url=self.BASE_URL
        )
    
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
    
    def create_messages(self) -> List[Dict[str, str]]:
        """Create the conversation messages for the ecommerce assistant."""
        return [
            {
                "role": "system",
                "content": (
                    "Use the given compute function tool to perform calculations. Do not perform calculations youself. If tool fails then just return sorry message with cause of sorry message."
                )
            },
            {
                "role": "user",
                "content": "give discount of 5% on $100"
            }
        ]
    
    def run_streaming_chat_completion(self) -> None:
        print("🔄 AgC Streaming Chat Completion with Python Function Tool Calling")
        
        try:
            messages = self.create_messages()
            tools = [self.create_py_fun_tool()]
            
            print(f"🤖 Model: {self.MODEL}")
            print("--- 🌊 Streaming Response ---")
            
            stream = self.client.chat.completions.create(
                model=self.MODEL,
                messages=messages,
                tools=tools,
                tool_choice=None,
                stream=True
            )
            
            for chunk in stream:
                self.process_stream_chunk(chunk)
            
            print("\n--- ✅ Streaming Complete ---")
            
        except Exception as e:
            print(f"\n❌ Error: {e}")
            import traceback
            traceback.print_exc()

    def process_stream_chunk(self, chunk) -> None:
        """Process each streaming chunk and display content."""
        if not chunk.choices or len(chunk.choices) == 0:
            return
        
        choice = chunk.choices[0]
        
        # Display content delta immediately for real-time streaming
        if choice.delta and choice.delta.content:
            content = choice.delta.content
            if content:
                print(content, end='', flush=True)
        
        # Display tool call information
        if choice.delta and choice.delta.tool_calls:
            print(f"\n🔧 [Tool calls detected]")
            for tool_call in choice.delta.tool_calls:
                if tool_call.function:
                    print(f"   ⚙️  {tool_call.function.name}")
        
        # Display finish reason
        if choice.finish_reason:
            print(f"\n🏁 [Finish: {choice.finish_reason}]")

    def run(self) -> None:
        """Run the AgC example."""
        print("🚀 AgC Loop with Python Function tool Example")
        self.run_streaming_chat_completion()


def main():
    example = AgCLoopWithPyFunToolExample()
    example.run()


if __name__ == "__main__":
    main()
