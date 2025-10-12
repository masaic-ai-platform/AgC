# Platform Server: The Agentic Orchestration API Layer

## Overview

**Open Source • Community-Driven • Apache 2.0 Licensed**

> **Built-in Agentic Tools. Built-in Orchestration. Built-in MCP Support. Any Model.**  
> No glue code. Use **any framework** that speaks Completions or Responses API<sup>1</sup>.

<sup>1</sup> OpenAI Agents SDK, Autogen, LangGraph, CrewAI, Agno Agents, LMOS ARC, and more.

**Platform Server** is an agentic orchestration API layer that provides drop-in replacement for `/responses`, `/chat/completions`, and `/embeddings` — now with:

- 🔧 **Built-in agentic tool/function calling**
- ✅ **Built-in Remote MCP server Integrations**
- 🔍 **Built-in Search + RAG**
- 🧠 **Built-in Agentic state + memory**
- 🎯 **Built-in agent orchestration capabilities**

Works even if your model lacks native support — like [OpenAI's Responses API](https://platform.openai.com/docs/api-reference/responses).

## 🔧 Key Engineering Wins

🧠 **Built-In Agentic Tools, Server-Side**  
RAG, file/web search, memory, and remote MCP server integrations — all built-in with zero glue code.

⚡ **Fast, Flexible, and Fully Open**  
Supports any model, stateful responses, and tool/function calling — lightweight, high-performance, and easy to self-host.

🎯 **Agentic Orchestration**  
Multi-agent workflows, collaboration, and agent-to-agent messaging capabilities built-in.

## 🚀 Getting Started

Get up and running in **2 steps** — an agentic orchestration API with tool calling, RAG, memory, and remote MCP, powered by **your models**.

### 🐳 Run with Docker

```bash
docker run -p 6644:6644 masaicai/agc-platform-server:latest
```

### Using with OpenAI SDK

```python
openai_client = OpenAI(base_url="http://localhost:6644/v1", api_key=os.getenv("XAI_API_KEY"))

response = openai_client.responses.create(
    model="xai@grok-4-0709",
    input="Write a poem on Masaic"
)
```

### Using with OpenAI Agent SDK

```python
client = AsyncOpenAI(base_url="http://localhost:6644/v1", api_key=os.getenv("XAI_API_KEY"))
agent = Agent(
    name="Assistant",
    instructions="You are a humorous poet who can write funny poems of 4 lines.",
    model=OpenAIResponsesModel(model="xai@grok-4-0709", openai_client=client)
)
```

### Using with cURL

```bash
curl --location 'http://localhost:6644/v1/responses' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer XAI_API_KEY' \
--data '{
    "model": "xai@grok-4-0709",
    "stream": false,
    "input": [
        {
            "role": "user",
            "content": "Write a poem on Masaic"
        }
    ]
}'
```

## Core Capabilities

| Feature | Description | Benefit |
|---------|-------------|---------|
| **Agentic Orchestration** | Multi-agent workflows and collaboration | Build complex agent systems with minimal code |
| **Automated Tracing** | Comprehensive request and response monitoring | Track performance and usage without additional code |
| **Integrated RAG** | Contextual information retrieval | Enhance responses with relevant external data automatically |
| **Pre-built Tool Integrations** | Web search, GitHub access, and more | Deploy advanced capabilities instantly |
| **Self-Hosted Architecture** | Full control of deployment infrastructure | Maintain complete data sovereignty |
| **OpenAI-Compatible Interface** | Drop-in replacement for existing OpenAI implementations | Minimal code changes for migration |

## API Reference

The API implements the following OpenAI-compatible endpoints:

### Responses API

| Endpoint | Description |
|----------|-------------|
| `POST /v1/responses` | Create a new model response | 
| `GET /v1/responses/{responseId}` | Retrieve a specific response | 
| `DELETE /v1/responses/{responseId}` | Delete a response | 
| `GET /v1/responses/{responseId}/input_items` | List input items for a response | 

### Completions API

| Endpoint | Description |
|----------|-------------|
| `POST /v1/chat/completions` | Create a chat completion (OpenAI compatible) |
| `GET /v1/chat/completions/{completionId}` | Retrieve a specific chat completion |
| `DELETE /v1/chat/completions/{completionId}` | Delete a chat completion |

### File Management

| Endpoint | Description |
|----------|-------------|
| `POST /v1/files` | Upload a file |
| `GET /v1/files` | List uploaded files |
| `GET /v1/files/{file_id}` | Retrieve file metadata |
| `DELETE /v1/files/{file_id}` | Delete a file |
| `GET /v1/files/{file_id}/content` | Retrieve file content |

### Vector Store Operations

| Endpoint | Description |
|----------|-------------|
| `POST /v1/vector_stores` | Create a vector store |
| `GET /v1/vector_stores` | List vector stores |
| `GET /v1/vector_stores/{vector_store_id}` | Retrieve a vector store |
| `POST /v1/vector_stores/{vector_store_id}` | Modify a vector store |
| `DELETE /v1/vector_stores/{vector_store_id}` | Delete a vector store |
| `POST /v1/vector_stores/{vector_store_id}/search` | Search a vector store |
| `POST /v1/vector_stores/{vector_store_id}/files` | Add a file to a vector store |
| `GET /v1/vector_stores/{vector_store_id}/files` | List files in a vector store |
| `GET /v1/vector_stores/{vector_store_id}/files/{file_id}` | Retrieve a vector store file |
| `GET /v1/vector_stores/{vector_store_id}/files/{file_id}/content` | Retrieve vector store file content |
| `POST /v1/vector_stores/{vector_store_id}/files/{file_id}` | Update vector store file attributes |
| `DELETE /v1/vector_stores/{vector_store_id}/files/{file_id}` | Delete a file from a vector store |

### Evaluations API

| Endpoint | Description |
|----------|-------------|
| `POST /v1/evals` | Create a new evaluation |
| `GET /v1/evals/{evalId}` | Retrieve a specific evaluation |
| `GET /v1/evals` | List evaluations with pagination and filtering |
| `DELETE /v1/evals/{evalId}` | Delete an evaluation |
| `POST /v1/evals/{evalId}` | Update an evaluation |
| `POST /v1/evals/{evalId}/runs` | Create a new evaluation run |
| `GET /v1/evals/{evalId}/runs/{runId}` | Retrieve a specific evaluation run |
| `GET /v1/evals/{evalId}/runs` | List evaluation runs for a specific evaluation |
| `DELETE /v1/evals/{evalId}/runs/{runId}` | Delete an evaluation run |

### Setting Up the OpenTelemetry Collector

1. To enable the OpenTelemetry collector integration, start the [service](open-responses-server) with:
   ```
   OTEL_SDK_DISABLED=false
   ```

2. The OpenTelemetry collector collects data from the service using OTLP (OpenTelemetry Protocol).

3. Configuration of the collector is done via its config file, typically located in the deployment environment.

4. All opentelemetry [sdk-environment-variables](https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/) are supported.

### Platform Configuration

The following environment variables can be configured to deploy a full-fledged AgC platform:

| Environment Variable | Description | Default Value | Example Value |
|---------------------|-------------|---------------|---------------|
| `SPRING_PROFILES_ACTIVE` | Profile to activate when booting AgC as full-fledged platform | `default` | `platform` |
| `OPEN_RESPONSES_STORE_VECTOR_SEARCH_PROVIDER` | Vector store provider to use | `file` | `qdrant` or `file` |
| `OPEN_RESPONSES_STORE_VECTOR_SEARCH_QDRANT_HOST` | Qdrant vector store host name | - | `your-qdrant-host.com` |
| `OPEN_RESPONSES_STORE_VECTOR_SEARCH_QDRANT_API_KEY` | API key for Qdrant vector store | - | `your-qdrant-api-key` |
| `OPEN_RESPONSES_STORE_VECTOR_SEARCH_QDRANT_USE_TLS` | Enable TLS for secure Qdrant connection | `false` | `true` or `false` |
| `OPEN_RESPONSES_STORE_VECTOR_REPOSITORY_TYPE` | Repository type for vector operations | `file` | `mongodb` or `file` |
| `OPEN_RESPONSES_STORE_TYPE` | Data store type for platform | `in-memory` | `mongodb` or `in-memory` |
| `PLATFORM_DEPLOYMENT_AUTH_ENABLED` | Enable authentication for the platform | `false` | `true` or `false` |
| `PLATFORM_DEPLOYMENT_AUTH_GOOGLE_AUDIENCE` | Google Auth audience when platform auth is enabled | - | `your-google-auth-audience` |

## MCP Server Integration

### Configuring AgC with MCP Server

To enable MCP server functionality, add these additional environment variables to your platform configuration:

| Environment Variable | Description | Example Value |
|---------------------|-------------|---------------|
| `PLATFORM_DEPLOYMENT_MCP-SERVER_ENABLED` | Enable/disable MCP server | `true` or `false` |
| `PLATFORM_DEPLOYMENT_MCP-SERVER_VALIDAPIKEYS` | Comma-separated list of valid API keys for MCP client access | `key1,key2,key3` |
| `PLATFORM_DEPLOYMENT_PROVIDERS_CLAUDE_APIKEY` | Claude API key for agents invoked through MCP askAgent tool | `sk-ant-your-claude-api-key` |

**Note:** For multiple providers, use the convention `PLATFORM_DEPLOYMENT_PROVIDERS_{PROVIDER}_APIKEY` (e.g., `PLATFORM_DEPLOYMENT_PROVIDERS_OPENAI_APIKEY`).

### Connecting to AgC MCP Server

#### Option 1: Remote MCP Connection (Any MCP-compatible app)

For any application that supports remote MCP server connections:

- **MCP URL**: `https://{host_name}/mcp` (for production) or `https://localhost:6644/mcp` (for local deployment)
- **API Key**: Use one of the keys specified in `PLATFORM_DEPLOYMENT_MCP-SERVER_VALIDAPIKEYS`

#### Option 2: Claude Desktop (Local Connection)

**Prerequisites:**
- Node.js version ≥ 23.11.x
- Install the `mcp-remote` module: `npm install -g mcp-remote`

**Setup Steps:**
1. Open Claude Desktop Settings → Developer → Edit Config
2. This will open the `claude_desktop_config.json` file
3. Add the following MCP server configuration:

```json
{
  "mcpServers": {
    "AgC": {
      "command": "npx",
      "args": ["mcp-remote", "https://{host_name}/mcp", "--allow-http", "--header", "Authorization: Bearer ${MCP_API_KEY}"],
      "env": {
        "APP_MODE": "http",
        "MCP_API_KEY": "your-valid-mcp-api-key"
      }
    }
  }
}
```

**For local deployment, use:**
```json
{
  "mcpServers": {
    "AgC": {
      "command": "npx",
      "args": ["mcp-remote", "https://localhost:6644/mcp", "--allow-http", "--header", "Authorization: Bearer ${MCP_API_KEY}"],
      "env": {
        "APP_MODE": "http",
        "MCP_API_KEY": "your-valid-mcp-api-key"
      }
    }
  }
}
```

4. Replace `your-valid-mcp-api-key` with one of the keys from your `PLATFORM_DEPLOYMENT_MCP-SERVER_VALIDAPIKEYS` configuration
5. Save the config file and restart Claude Desktop
6. You should now see "AgC" listed in your MCP servers

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

Before submitting a Pull Request, please ensure all regression tests pass by running:

```bash
./regression/regression_common.sh
./regression/regression_vector.sh
```
---

<p align="center">
  Made with ❤️ by the Masaic AI Team
</p>
