# Function Registry Implementation

This package implements a minimal Function Registry API for AgC (Open-Responses) as specified in the `function-registry-minimal-spec.md`.

## Overview

The Function Registry allows analysts to persist and serve compute functions authored in Jupyter notebooks. It provides a REST API for CRUD operations on functions without implementing schema inference (to be added later).

## Architecture

### Components

1. **Models** (`FunctionRegistryModels.kt`)
   - `FunctionDoc`: MongoDB document model
   - `FunctionCreate`: Request model for creation
   - `FunctionUpdate`: Request model for updates
   - `FunctionListResponse`: Response model for listing
   - Error models and constants

2. **Validation** (`FunctionRegistryValidator.kt`)
   - Function name validation (regex pattern)
   - Pip requirements validation
   - Python code validation
   - Request validation

3. **Repository** (`FunctionRegistryRepository.kt`)
   - Interface defining CRUD operations
   - MongoDB implementation with reactive support
   - In-memory implementation for testing

4. **Service** (`FunctionRegistryService.kt`)
   - Business logic layer
   - Validation coordination
   - Error handling

5. **Controller** (`FunctionRegistryController.kt`)
   - REST API endpoints
   - HTTP status code handling
   - Request/response mapping

6. **Configuration** (`FunctionRegistryConfig.kt`)
   - Repository selection based on storage type

## API Endpoints

### Base Path: `/api/registry`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/functions` | Create a new function |
| GET | `/functions/{name}` | Get function by name |
| GET | `/functions` | List functions with search/pagination |
| PUT | `/functions/{name}` | Update function |
| DELETE | `/functions/{name}` | Delete function |
| POST | `/functions:preview` | Preview function (placeholder) |

### Query Parameters

- `q`: Search query for function names
- `limit`: Maximum results (max 100)
- `cursor`: Pagination cursor (placeholder)
- `includeNotebook`: Include notebook content in list response

## Data Model

### Function Document

```json
{
  "_id": "function_name",
  "name": "function_name",
  "description": "Function description",
  "runtime": { "kind": "python" },
  "deps": ["pandas==2.2.2", "numpy>=1.21.0"],
  "code": "def run(params): return {'result': 'success'}",
  "inputSchema": null,  // To be populated by schema inference
  "outputSchema": null, // To be populated by schema inference
  "createdAt": "2025-01-13T10:00:00Z",
  "updatedAt": "2025-01-13T10:00:00Z"
}
```

### Validation Rules

- **Name**: `^[a-zA-Z_][a-zA-Z0-9_-]{2,64}$`
- **Dependencies**: Valid pip requirement strings
- **Code**: Non-blank Python code string
- **Description**: Non-blank string

## Storage

### MongoDB Collection
- **Collection**: `function_registry`
- **Index**: `_id` (function name)

### In-Memory (Default)
- Caffeine cache with LRU eviction
- Maximum size: 1000 functions
- Useful for testing and development

## Configuration

The repository implementation is automatically selected based on the `open-responses.store.type` property:

- `mongodb`: Uses `MongoFunctionRegistryRepository`
- `in-memory` (default): Uses `InMemoryFunctionRegistryRepository`

## Error Handling

The API returns appropriate HTTP status codes and error messages:

- `400 Bad Request`: Validation errors
- `404 Not Found`: Function not found
- `409 Conflict`: Name conflict on creation
- `422 Unprocessable Entity`: Schema inference failed (placeholder)
- `500 Internal Server Error`: Unexpected errors

## Future Enhancements

1. **Schema Inference**: Implement automatic schema extraction from notebooks
2. **Cursor Pagination**: Add proper cursor-based pagination
3. **Advanced Search**: Full-text search across function content
4. **Versioning**: Function version management
5. **Execution**: Function execution capabilities

## Testing

Run the validation tests:

```bash
./gradlew test --tests "ai.masaic.platform.api.registry.functions.FunctionRegistryValidatorTest"
```

## Usage Example

### Create a Function

```bash
curl -X POST http://localhost:8080/api/registry/functions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "simulate_service_uplift",
    "description": "Simulate +10% uplift for a service.",
    "deps": ["pandas==2.2.2", "numpy>=1.21.0"],
    "code": "def run(params): return {'result': 'success'}"
  }'
```

### List Functions

```bash
curl "http://localhost:8080/api/registry/functions?limit=10&q=simulate"
```

### Get Function

```bash
curl "http://localhost:8080/api/registry/functions/simulate_service_uplift"
```

### Update Function

```bash
curl -X PUT http://localhost:8080/api/registry/functions/simulate_service_uplift \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Updated description"
  }'
```

### Delete Function

```bash
curl -X DELETE http://localhost:8080/api/registry/functions/simulate_service_uplift
```
