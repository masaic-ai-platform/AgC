# AgC Test Regression Server

The AgC Test Regression Server is a specialized server for running regression test suites against AgC (Agent Controller).

## Features

### Response Store Facade

The regression server includes a facade layer (`RegSuiteResponseStoreFacade`) that allows flexible access to response data in two modes:

#### 1. In-Memory Mode (Default)
Uses the embedded `ResponseStoreService` directly. This is useful when running AgC environment in-memory within the same application.

**Configuration:**
```properties
platform.regression.response-store.mode=in-memory
```

#### 2. HTTP Mode
Fetches response data via HTTP APIs from a remote AgC instance. This is useful when testing against a separate AgC deployment.

**Configuration:**
```properties
platform.regression.response-store.mode=http
platform.regression.response-store.api-base-url=http://localhost:6644
```

### APIs Used in HTTP Mode

When configured for HTTP mode, the facade calls:
- `GET /v1/responses/{response_id}/input_items` - Retrieves input items for a response
- `GET /v1/responses/{response_id}` - Retrieves the complete response

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `platform.regression.response-store.mode` | `in-memory` | Mode of operation: `in-memory` or `http` |
| `platform.regression.response-store.api-base-url` | `http://localhost:6644` | Base URL for HTTP API calls (used only in HTTP mode) |

## Running the Server

### Standard Run
```bash
./gradlew :agc-test-regression-server:bootRun
```

### With UI (Full Suite)
Use the provided shell script to launch both the regression server and UI:

```bash
./platform/agc-test-regression-server/run-regression-suite.sh
```

Or from within the agc-test-regression-server directory:

```bash
cd platform/agc-test-regression-server
./run-regression-suite.sh
```

This script will:
- Start the regression server on port 6649
- Start the UI application on port 6650
- Configure the UI to connect to the backend at http://localhost:6649

### Access Points
- **Backend:** http://localhost:6649
- **Frontend:** http://localhost:6650 (when using the suite script)

## MCP Tools Available

The regression server provides MCP (Model Context Protocol) tools:

### 1. `run_play_wright_script`
Executes a Playwright test script.

**Parameters:**
- `scriptName` (required): Name of the Playwright script to run

**Example:**
```json
{
  "scriptName": "test-login.ts"
}
```

### 2. `get_test_steps_trail`
Retrieves the conversational trail of messages from a test run.

**Parameters:**
- `responseId` (required): Response ID from the test run

**Example:**
```json
{
  "responseId": "resp_123abc"
}
```

## Architecture

```
RegTestSuiteAgent
    ↓
RegServerMcpClientFactory
    ↓
RegSuiteMcpClient
    ↓
RegSuiteResponseStoreFacade
    ↓
    ├─→ [in-memory] ResponseStoreService (direct)
    └─→ [http] HTTP API calls to remote AgC
```

## Development

### Adding New Tools

To add new MCP tools to the regression suite:

1. Define the tool in `RegSuiteMcpClient`
2. Add tool execution logic in the `executeTool` method
3. Update the tool list in `listTools` method

### Testing Different Modes

**Test In-Memory Mode:**
```bash
# Use default configuration
./gradlew :agc-test-regression-server:bootRun
```

**Test HTTP Mode:**
```bash
# Set environment variables or update application.properties
export PLATFORM_REGRESSION_RESPONSE_STORE_MODE=http
export PLATFORM_REGRESSION_RESPONSE_STORE_API_BASE_URL=http://localhost:6644
./gradlew :agc-test-regression-server:bootRun
```

## Troubleshooting

### HTTP Connection Issues

If you're using HTTP mode and getting connection errors:
1. Verify the target AgC server is running
2. Check the `api-base-url` configuration is correct
3. Ensure the APIs are accessible from the regression server
4. Check logs for detailed error messages

### Response Not Found Errors

If you get "Response not found" errors:
1. Verify the `responseId` is correct
2. Check that the response exists in the target store
3. Ensure you're using the correct mode (in-memory vs http)
