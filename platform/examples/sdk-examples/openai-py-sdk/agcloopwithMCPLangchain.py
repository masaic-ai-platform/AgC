import os
import sys
from typing import Dict, List, Any, Literal
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.tools import tool
from langchain_core.callbacks.streaming_stdout import StreamingStdOutCallbackHandler
from pydantic import BaseModel, Field


class AllbirdsMCPToolInput(BaseModel):
    """Input schema for Allbirds MCP tool with strict validation."""
    
    type: Literal["mcp"] = Field(
        description="Tool type identifier for MCP server communication",
        default="mcp"
    )
    server_label: Literal["allbirds"] = Field(
        description="Unique identifier for the Allbirds MCP server",
        default="allbirds"
    )
    server_url: Literal["https://allbirds.com/api/mcp"] = Field(
        description="Endpoint URL for the Allbirds MCP server API",
        default="https://allbirds.com/api/mcp"
    )


@tool(args_schema=AllbirdsMCPToolInput)
def allbirds_mcp_tool_action_YWxsYmlyZH(
    type: Literal["mcp"] = "mcp",
    server_label: Literal["allbirds"] = "allbirds",
    server_url: Literal["https://allbirds.com/api/mcp"] = "https://allbirds.com/api/mcp"
) -> Dict[str, Any]:
    """Allbirds MCP server tool for e-commerce operations.
    
    This tool connects to the Allbirds Model Context Protocol (MCP) server
    to perform e-commerce operations including:
    - Product search and discovery
    - Shopping cart management
    - Checkout link generation
    
    Use this tool when you need to:
    - Search for Allbirds products (shoes, apparel, accessories)
    - Add items to shopping cart
    - Retrieve checkout URLs for customers
    - Get product details, pricing, and availability
    
    Args:
        type: MCP protocol type identifier (always "mcp")
        server_label: Server identifier for routing (always "allbirds")
        server_url: API endpoint for the Allbirds MCP server
        
    Returns:
        Dictionary containing the tool execution result from the MCP server
    """
    # This function signature is required for AgC platform integration
    # The actual execution is handled by the AgC platform MCP middleware
    return {
        "type": type,
        "server_label": server_label,
        "server_url": server_url
    }


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
            
            # Convert LangChain tool to OpenAI function format for proper serialization
            from langchain_core.utils.function_calling import convert_to_openai_tool
            tool_definition = convert_to_openai_tool(allbirds_mcp_tool_action_YWxsYmlyZH)
            
            # Bind tools to the LLM
            llm_with_tools = self.llm.bind(
                tools=[tool_definition],
                tool_choice=None
            )
            
            print(f"🤖 Model: {self.MODEL}")
            print("--- 🌊 Streaming Response ---")
            
            # Stream the response - StreamingStdOutCallbackHandler prints automatically
            # Note: AgC platform handles MCP tool execution server-side and returns
            # results as content, so no tool calls will appear in the stream
            for chunk in llm_with_tools.stream(messages):
                pass  # StreamingStdOutCallbackHandler handles output
            
            print("\n--- ✅ Streaming Complete ---")
            
        except Exception as e:
            print(f"\n❌ Error: {e}")
            import traceback
            traceback.print_exc()

    def run(self) -> None:
        """Run the AgC example."""
        print("🚀 AgC Loop with MCP Example (LangChain)")
        self.run_streaming_chat_completion()


def main():
    example = AgCLoopWithMCPLangChainExample()
    example.run()


if __name__ == "__main__":
    main()

