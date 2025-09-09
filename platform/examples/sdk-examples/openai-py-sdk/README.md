# AgC OpenAI Python SDK Example

This example demonstrates how to use the OpenAI Python SDK to interact with the AgC (Agentic Commerce) platform for ecommerce assistance with streaming chat completions and MCP (Model Context Protocol) tool calling.

## Overview

The example shows how to:
- Connect to the AgC API endpoint (`http://localhost:6644/v1`) using the official OpenAI Python SDK
- Use streaming chat completions with the OpenAI Python SDK client
- Integrate with Allbirds MCP (Model Context Protocol) tools
- Handle streaming responses with proper error handling
- Use the official OpenAI Python SDK for maximum compatibility and reliability

## Prerequisites

1. **Python 3.8 or higher**
2. **AgC Platform running** on `localhost:6644`
3. **Valid API key** for the model provider
4. **pip** for package management

## Setup

### 1. Install Dependencies

```bash
# Navigate to the Python SDK example directory
cd platform/examples/sdk-examples/openai-py-sdk

# Create a virtual environment (recommended)
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install required packages
pip install -r requirements.txt
```

### 2. Configure API Key

You can set your API key in two ways:

**Option A: Environment Variable (Recommended)**
```bash
# Set the environment variable
export OPENAI_API_KEY="your_api_key_here"

```

**Option B: Direct Code Edit**
- Edit `agcloopwithMCP.py`
- Update the `API_KEY` constant with your valid API key

## Running the Example

### Basic Usage

```bash
# Make sure you're in the correct directory and virtual environment is activated
cd platform/examples/sdk-examples/openai-py-sdk
source venv/bin/activate

# Run the example
python agcloopwithMCP.py
```
