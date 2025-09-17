import os
import sys
from typing import Dict, List, Any
from openai import OpenAI


class AgCLoopWithMCPExample:
    """Minimal AgC example with MCP tool calling."""
    
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
    
    def create_allbirds_mcp_tool(self) -> Dict[str, Any]:
        """Create the Allbirds MCP tool configuration."""
        return {
            "type": "function",
            "function": {
                "name": "allbirds_mcp_tool_action_YWxsYmlyZH",
                "description": "Allbirds MCP server tool",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "enum": ["mcp"]
                        },
                        "server_label": {
                            "type": "string",
                            "enum": ["allbirds"]
                        },
                        "server_url": {
                            "type": "string",
                            "enum": ["https://allbirds.com/api/mcp"]
                        }
                    },
                    "required": ["type", "server_label", "server_url"],
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
                    "You are an ecommerce assistant who can help in product selection, add to cart, provide checkout link.\n"
                    "1. Use all birds tool for product search and update cart.\n"
                    "2. return image url in a proper markdown format.\n"
                    "3. whenever you update cart, share checkout link also."
                )
            },
            {
                "role": "user",
                "content": "Find me sneakers of size 8"
            }
        ]
    
    def run_streaming_chat_completion(self) -> None:
        """Run a streaming chat completion with MCP tool calling."""
        print("🔄 AgC Streaming Chat Completion with MCP Tool Calling")
        
        try:
            messages = self.create_messages()
            tools = [self.create_allbirds_mcp_tool()]
            
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
        print("🚀 AgC Loop with MCP Example")
        self.run_streaming_chat_completion()


def main():
    example = AgCLoopWithMCPExample()
    example.run()


if __name__ == "__main__":
    main()
