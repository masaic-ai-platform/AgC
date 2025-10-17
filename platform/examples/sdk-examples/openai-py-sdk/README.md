# AgC Python SDK Examples

This directory contains comprehensive examples demonstrating how to use the OpenAI Python SDK and LangChain to interact with the AgC (Agentic Commerce) platform. These examples showcase various tool types including MCP servers, file search, Python function execution, and hybrid agentic loops.

## 📚 Overview

The examples demonstrate how to:
- Connect to the AgC API endpoint (`http://localhost:6644/v1`) using the official OpenAI Python SDK
- Use streaming chat completions for real-time responses
- Integrate with different tool types (MCP, File Search, Python Functions, Local Tools)
- Handle streaming responses with proper error handling
- Build agentic loops that combine multiple tools
- Use LangChain as an alternative framework for building agentic applications

---

## 📋 Available Examples

### 🔧 MCP Tool Examples

#### 1. **agcloopwithMCP.py** - MCP Tool with Allbirds E-commerce
**Description:** Demonstrates MCP (Model Context Protocol) tool calling with Allbirds e-commerce server for product search, cart management, and checkout.

**Use Case:** E-commerce assistant for product discovery and purchase flow

**Tools Used:**
- Allbirds MCP server (`allbirds_mcp_tool_action`)

**Example Query:** "Find me sneakers of size 8"

**Run:**
```bash
python agcloopwithMCP.py
```

#### 2. **agcloopwithMCPLangchain.py** - MCP Tool with LangChain
**Description:** Same functionality as `agcloopwithMCP.py` but implemented using LangChain framework with typed messages and built-in streaming callbacks.

**Use Case:** E-commerce assistant using LangChain abstractions

**Tools Used:**
- Allbirds MCP server (via LangChain)

**Example Query:** "Find me sneakers of size 8"

**Run:**
```bash
python agcloopwithMCPLangchain.py
```

---

### 📁 File Search / RAG Examples

#### 3. **agcloopwithFileSearch.py** - Vector Store File Search
**Description:** Demonstrates file search tool with vector store integration for RAG (Retrieval Augmented Generation) use cases.

**Use Case:** Knowledge base search for chemical reactions

**Tools Used:**
- File search tool with vector store
- Embedding model: `openai@text-embedding-3-small`

**Prerequisites:**
- Vector store ID: `vs_68b98d857f73cf000000` (configure your own)
- Documents uploaded to the vector store

**Example Query:** "Find one reaction of Magnesium"

**Run:**
```bash
python agcloopwithFileSearch.py
```

---

### 🐍 Python Function Execution Examples

#### 4. **agcloopwithPyFunTool.py** - Python Function Tool
**Description:** Demonstrates Python function execution tool using E2B code interpreter for dynamic computation.

**Use Case:** Discount calculator using Python code execution

**Tools Used:**
- Python function tool (`py_fun_tool`)
- E2B code interpreter server

**Prerequisites:**
- `E2B_API_KEY` environment variable
- E2B server running on `http://localhost:8000/mcp`

**Example Query:** "Give discount of 5% on $100"

**Run:**
```bash
export E2B_API_KEY="your_e2b_api_key"
python agcloopwithPyFunTool.py
```

---

### 🔄 Hybrid Agentic Loop Examples

#### 5. **agcloopwithLocalTool.py** - Multi-Tool Agentic Loop
**Description:** Advanced example demonstrating an agentic loop that combines local tool execution with remote Python function tools. Shows multi-turn conversations with tool chaining.

**Use Case:** Discount calculation system with customer segmentation

**Tools Used:**
- Local tool: `get_discount_percentage` (executed locally)
- Python function tool: `discount_calculator_macro` (executed via E2B)

**Prerequisites:**
- `E2B_API_KEY` environment variable
- E2B server running on `http://localhost:8000/mcp`

**Example Query:** "Offer $100 sneakers to premium customers."

**Model:** `claude@claude-3-7-sonnet-20250219`

**Run:**
```bash
export E2B_API_KEY="your_e2b_api_key"
python agcloopwithLocalTool.py
```

**Key Features:**
- Multi-turn agentic loop
- Local tool execution (in-process)
- Remote Python function execution (E2B)
- Tool chaining and orchestration

---

## 🔧 Prerequisites

### General Requirements
1. **Python 3.8 or higher**
2. **AgC Platform running** on `localhost:6644`
3. **Valid API key** for the model provider (OpenAI, Anthropic, etc.)
4. **pip** for package management

### Example-Specific Requirements

| Example | Additional Requirements |
|---------|------------------------|
| `agcloopwithMCP.py` | None |
| `agcloopwithMCPLangchain.py` | LangChain packages |
| `agcloopwithFileSearch.py` | Vector store with uploaded documents |
| `agcloopwithPyFunTool.py` | E2B API key, E2B server running |
| `agcloopwithLocalTool.py` | E2B API key, E2B server running |

---

## 🚀 Setup

### 1. Install Dependencies

```bash
# Navigate to the Python SDK example directory
cd platform/examples/sdk-examples/openai-py-sdk

# Create a virtual environment (recommended)
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install all required packages
pip install -r requirements.txt
```

### 2. Configure API Keys

You can set your API keys in two ways:

**Option A: Environment Variables (Recommended)**
```bash
# Required for all examples
export OPENAI_API_KEY="your_api_key_here"

# Required for Python function and hybrid examples
export E2B_API_KEY="your_e2b_api_key_here"
```

**Option B: Direct Code Edit**
- Edit the specific example file you want to run
- Update the `API_KEY` and/or `E2B_API_KEY` constants with your valid API keys

---

## 🎯 Running the Examples

### Quick Start

```bash
# Make sure you're in the correct directory and virtual environment is activated
cd platform/examples/sdk-examples/openai-py-sdk
source venv/bin/activate

# Run any example
python <example_name>.py
```

### Example Commands

```bash
# MCP tool with OpenAI SDK
python agcloopwithMCP.py

# MCP tool with LangChain
python agcloopwithMCPLangchain.py

# File search tool
python agcloopwithFileSearch.py

# Python function tool
python agcloopwithPyFunTool.py

# Hybrid agentic loop
python agcloopwithLocalTool.py
```

---

## 📊 Example Output

All examples produce similar streaming output format:

```
🚀 AgC Loop with [Tool Type] Example
🔄 AgC Streaming Chat Completion with [Tool Type] Tool Calling
🤖 Model: openai@gpt-4.1-mini
--- 🌊 Streaming Response ---
[Streaming content will appear here in real-time]
🔧 [Tool calls detected]
   ⚙️  tool_name
🏁 [Finish: tool_calls/stop]
--- ✅ Streaming Complete ---
```

### Sample Outputs by Example

**MCP Tool (Allbirds):**
```
🚀 AgC Loop with MCP Example
🔄 AgC Streaming Chat Completion with MCP Tool Calling
🤖 Model: openai@gpt-4.1-mini
--- 🌊 Streaming Response ---
I found several sneakers in size 8:
![Sneaker Image](https://example.com/sneaker.jpg)
Checkout: https://allbirds.com/checkout/abc123
🔧 [Tool calls detected]
   ⚙️  allbirds_mcp_tool_action_YWxsYmlyZH
--- ✅ Streaming Complete ---
```

**File Search Tool:**
```
🚀 AgC Loop with File Search tool Example
🔄 AgC Streaming Chat Completion with File Search Tool Calling
🤖 Model: openai@gpt-4.1-mini
--- 🌊 Streaming Response ---
One reaction of Magnesium: Mg + O₂ → MgO (Magnesium oxide formation)
🔧 [Tool calls detected]
   ⚙️  file-search-tool
--- ✅ Streaming Complete ---
```

**Python Function Tool:**
```
🚀 AgC Loop with Python Function tool Example
🔄 AgC Streaming Chat Completion with Python Function Tool Calling
🤖 Model: openai@gpt-4.1-mini
--- 🌊 Streaming Response ---
After applying a 5% discount on $100, the final price is $95.00
🔧 [Tool calls detected]
   ⚙️  python-function-tool
--- ✅ Streaming Complete ---
```

---

## 🔄 Comparison: OpenAI SDK vs LangChain

| Feature | OpenAI SDK | LangChain |
|---------|------------|-----------|
| **API Style** | Direct OpenAI client | High-level abstractions |
| **Messages** | Dict-based | Typed message objects (SystemMessage, HumanMessage) |
| **Streaming** | Manual chunk processing | Built-in StreamingStdOutCallbackHandler |
| **Tool Binding** | Pass tools in `create()` call | Use `.bind()` method |
| **Use Case** | Simple, direct API calls | Complex chains and workflows |
| **Learning Curve** | Lower - familiar OpenAI API | Higher - LangChain concepts |
| **Dependencies** | `openai` only | `langchain-openai`, `langchain-core` |
| **Flexibility** | Full control over API | Higher-level abstractions |

**When to use OpenAI SDK:**
- Simple tool calling scenarios
- Direct control over API parameters
- Minimal dependencies
- Familiar with OpenAI API

**When to use LangChain:**
- Complex multi-step workflows
- Need for chain composition
- Integration with LangChain ecosystem
- Building production agentic systems

---

## 🛠️ Tool Types Reference

### 1. MCP Tools (Model Context Protocol)
- **Purpose:** Connect to external MCP servers for specialized capabilities
- **Examples:** E-commerce APIs, CRM systems, custom business logic
- **Configuration:** Server URL, label, tool-specific parameters

### 2. File Search Tools
- **Purpose:** RAG (Retrieval Augmented Generation) with vector stores
- **Requirements:** Vector store ID, embedding model, uploaded documents
- **Use Cases:** Knowledge bases, document Q&A, semantic search

### 3. Python Function Tools
- **Purpose:** Execute custom Python code dynamically
- **Requirements:** E2B code interpreter, base64-encoded Python function
- **Use Cases:** Calculations, data transformations, custom logic
- **Execution:** Remote execution in sandboxed E2B environment

### 4. Local Tools
- **Purpose:** Execute functions locally within your application
- **Requirements:** Function implementation in your code
- **Use Cases:** Simple calculations, API calls, business logic
- **Execution:** In-process execution (fast, synchronous)

---

## 🏗️ Architecture

```
┌─────────────────┐
│  Your Python    │
│  Application    │
│  (SDK/LangChain)│
└────────┬────────┘
         │
         │ HTTP/REST
         ▼
┌─────────────────┐
│   AgC Platform  │
│  localhost:6644 │
└────────┬────────┘
         │
    ┌────┴────────────────┬──────────────┐
    │                     │              │
    ▼                     ▼              ▼
┌─────────┐      ┌──────────────┐  ┌────────────┐
│   MCP   │      │ Vector Store │  │  E2B Code  │
│ Servers │      │ (File Search)│  │ Interpreter│
└─────────┘      └──────────────┘  └────────────┘
```

---

## 🐛 Troubleshooting

### Common Issues

**1. API Key Not Set**
```
❌ Error: OPENAI_API_KEY environment variable not set
```
**Solution:** Set the environment variable or edit the code to include your API key

**2. AgC Platform Not Running**
```
Connection refused to localhost:6644
```
**Solution:** Ensure AgC platform is running on port 6644

**3. E2B API Key Missing**
```
❌ Error: E2B_API_KEY environment variable not set
```
**Solution:** Set E2B_API_KEY for Python function and hybrid examples

**4. Vector Store Not Found**
```
Vector store vs_xxx not found
```
**Solution:** Update the vector store ID in `agcloopwithFileSearch.py` with your own

**5. E2B Server Not Running**
```
Connection refused to localhost:8000
```
**Solution:** Start the E2B MCP server on port 8000

---

## 📝 Additional Resources

- [AgC Platform Documentation](../../README.md)
- [OpenAI Python SDK Documentation](https://platform.openai.com/docs/libraries/python-library)
- [LangChain Documentation](https://python.langchain.com/)
- [MCP Protocol Specification](https://github.com/anthropics/mcp)
- [E2B Code Interpreter](https://e2b.dev/)

---

## 💡 Next Steps

1. **Start Simple:** Begin with `agcloopwithMCP.py` to understand basic tool calling
2. **Explore LangChain:** Try `agcloopwithMCPLangchain.py` to see framework abstractions
3. **Add RAG:** Experiment with `agcloopwithFileSearch.py` for knowledge base integration
4. **Dynamic Execution:** Use `agcloopwithPyFunTool.py` for custom computations
5. **Build Complex Agents:** Study `agcloopwithLocalTool.py` for multi-tool orchestration

---

## 📄 License

See the main [LICENSE](../../../../LICENSE) file in the repository root.
