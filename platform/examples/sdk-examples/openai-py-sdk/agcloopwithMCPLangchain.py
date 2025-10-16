import os
import sys
from typing import Dict, List, Any
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.tools import tool
from langchain_core.callbacks.streaming_stdout import StreamingStdOutCallbackHandler


class AgCLoopWithMCPLangChainExample:
    """Minimal AgC example with MCP tool calling using LangChain."""
    
    # Configuration constants
    API_KEY = os.getenv("OPENAI_API_KEY", "")
    BASE_URL = "http://localhost:6644/v1"
    MODEL = "openai@gpt-4.1-mini"
    
    def __init__(self):
        """Initialize the AgC example with LangChain ChatOpenAI."""
        if not self.API_KEY:
            print("❌ Error: OPENAI_API_KEY environment variable not set")
            sys.exit(1)
        
        # Initialize LangChain ChatOpenAI with custom base URL
        self.llm = ChatOpenAI(
            api_key=self.API_KEY,
            base_url=self.BASE_URL,
            model=self.MODEL,
            streaming=True,
            callbacks=[StreamingStdOutCallbackHandler()]
        )
    
    def create_allbirds_mcp_tool(self) -> Dict[str, Any]:
        """Create the Allbirds MCP tool configuration for LangChain."""
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
    
    def create_messages(self) -> List[Any]:
        """Create the conversation messages for the ecommerce assistant."""
        system_content = (
            "You are an ecommerce assistant who can help in product selection, add to cart, provide checkout link.\n"
            "1. Use all birds tool for product search and update cart.\n"
            "2. return image url in a proper markdown format.\n"
            "3. whenever you update cart, share checkout link also."
        )
        
        return [
            SystemMessage(content=system_content),
            HumanMessage(content="Find me sneakers of size 8")
        ]
    
    def run_streaming_chat_completion(self) -> None:
        """Run a streaming chat completion with MCP tool calling using LangChain."""
        print("🔄 AgC Streaming Chat Completion with MCP Tool Calling (LangChain)")
        
        try:
            messages = self.create_messages()
            tools = [self.create_allbirds_mcp_tool()]
            
            # Bind tools to the LLM
            llm_with_tools = self.llm.bind(
                tools=tools,
                tool_choice=None
            )
            
            print(f"🤖 Model: {self.MODEL}")
            print("--- 🌊 Streaming Response ---")
            
            # Stream the response
            response = llm_with_tools.stream(messages)
            
            # Process streaming chunks
            for chunk in response:
                self.process_stream_chunk(chunk)
            
            print("\n--- ✅ Streaming Complete ---")
            
        except Exception as e:
            print(f"\n❌ Error: {e}")
            import traceback
            traceback.print_exc()

    def process_stream_chunk(self, chunk) -> None:
        """Process each streaming chunk and display relevant information."""
        # Display tool call information if present
        if hasattr(chunk, 'tool_calls') and chunk.tool_calls:
            print(f"\n🔧 [Tool calls detected]")
            for tool_call in chunk.tool_calls:
                print(f"   ⚙️  {tool_call.get('name', 'Unknown tool')}")
        
        # Display additional tool call info if present in kwargs
        if hasattr(chunk, 'additional_kwargs') and 'tool_calls' in chunk.additional_kwargs:
            tool_calls = chunk.additional_kwargs['tool_calls']
            if tool_calls:
                print(f"\n🔧 [Tool calls detected]")
                for tool_call in tool_calls:
                    if 'function' in tool_call:
                        print(f"   ⚙️  {tool_call['function'].get('name', 'Unknown')}")

    def run(self) -> None:
        """Run the AgC example."""
        print("🚀 AgC Loop with MCP Example (LangChain)")
        self.run_streaming_chat_completion()


def main():
    example = AgCLoopWithMCPLangChainExample()
    example.run()


if __name__ == "__main__":
    main()

