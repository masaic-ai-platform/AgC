import os
import sys
from typing import Dict, List, Any
from openai import OpenAI


class AgCLoopWithFileSearchExample:
    
    # Configuration constants
    API_KEY = os.getenv("OPENAI_API_KEY", "")
    BASE_URL = "http://localhost:6644/v1"
    MODEL = "openai@gpt-4.1-mini"
    
    def __init__(self):
        """Initialize the AgC example with OpenAI client."""
        if not self.API_KEY:
            print("❌ Error: OPENAI_API_KEY environment variable not set")
            sys.exit(1)
        
        self.client = OpenAI(
            api_key=self.API_KEY,
            base_url=self.BASE_URL
        )
    
    def create_file_search_tool(self) -> Dict[str, Any]:
        return {
            "type": "function",
            "function": {
                "name": "file-search-tool",
                "description": "This tool can make provide information about chemical reactions",
                "strict": True,
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
                                        self.API_KEY
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
                            "additionalProperties": False
                        }
                    },
                    "required": [
                        "type",
                        "vector_store_ids",
                        "modelInfo"
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
                    "Use the given file search tool to prepare answer. Do not frame any answer yourself, If tool fails then just return sorry message with cause of sorry message."
                )
            },
            {
                "role": "user",
                "content": "Find one reaction of Magnesium"
            }
        ]
    
    def run_streaming_chat_completion(self) -> None:
        print("🔄 AgC Streaming Chat Completion with File Search Tool Calling")
        
        try:
            messages = self.create_messages()
            tools = [self.create_file_search_tool()]
            
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
        print("🚀 AgC Loop with File Search tool Example")
        self.run_streaming_chat_completion()


def main():
    example = AgCLoopWithFileSearchExample()
    example.run()


if __name__ == "__main__":
    main()
