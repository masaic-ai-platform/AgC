# Function Registry API — Minimal Specification (Mongo-backed)

A minimal, implementation-ready API spec for a **Function Registry** over MongoDB, designed for AgC (Open-Responses) with strict but lean behavior. Matches the first-cut fields and derives I/O schemas from the notebook.

---

## 1) Scope & Goals

- Persist and serve **compute functions** authored by analysts.
- Keep fields minimal: `name`, `description`, `runtime.kind=python`, `deps`, `notebook`.
- **Derive** `inputSchema` and `outputSchema` from the notebook automatically on create/update.
- Store **timestamps** (`createdAt`, `updatedAt`) server-side only (not accepted from client).
- Ready to be called by: Chain Runner, Python Executor MCP tool, and LLM planners.

---

## 2) Entity: Function

**Collection**: `functions`

### Stored document (server-managed)
```json
{
  "_id": "simulate_service_uplift",
  "name": "simulate_service_uplift",
  "description": "Simulate +10% uplift for a service.",
  "runtime": { "kind": "python" },
  "deps": ["pandas==2.2.2", "pyarrow==16.1.0"],
  "notebook": { /* Jupyter .ipynb JSON (nbformat>=4) */ },

  "inputSchema":  { /* JSON Schema (inferred) */ },
  "outputSchema": { /* JSON Schema (inferred) */ },

  "createdAt": "2025-08-13T10:00:00Z",
  "updatedAt": "2025-08-13T10:00:00Z"
}
```

#### Constraints
- `name`: `^[a-zA-Z_][a-zA-Z0-9_-]{2,64}$` (unique).
- `deps`: array of **pip requirement strings** (exact text as in `requirements.txt`).
- `notebook`: valid **Jupyter Notebook JSON** (nbformat 4.x).

---

## 3) Schema Inference (server behavior)

On **create/update**, the server computes `inputSchema` and `outputSchema` from the notebook using this precedence (first match wins):

1. **Pydantic models**
   ```python
   from pydantic import BaseModel

   class Params(BaseModel): ...
   class Output(BaseModel): ...

   def run(params: Params) -> Output: ...
   ```
   - Persist:
     - `inputSchema  = Params.model_json_schema()`
     - `outputSchema = Output.model_json_schema()`

2. **Docstring YAML** on `run`
   ```python
   def run(params):
       \"\"\"
       ---
       inputSchema:  { ... }
       outputSchema: { ... }
       ---
       \"\"\"
       ...
   ```

3. **Schema cell**
   ```python
   INPUT_SCHEMA = { ... }   # JSON-serializable dict
   OUTPUT_SCHEMA = { ... }
   ```

If none found → **422 SCHEMA_INFERENCE_FAILED** with hints.

> Safety: execute only the notebook in a **restricted interpreter** (no network, CPU/memory/time limits). Only import standard libs and common data libs needed for schema extraction. Do not allow arbitrary side effects.

---

## 4) REST API (AgC service)

**Base path**: `/api/registry`

### Create
`POST /functions`

**Request body**
```json
{
  "name": "simulate_service_uplift",
  "description": "Simulate +10% uplift for a service.",
  "deps": ["pandas==2.2.2", "pyarrow==16.1.0"],
  "notebook": { /* ipynb JSON */ }
}
```

**Responses**
- `201 Created` — returns full stored doc (incl. inferred schemas, timestamps)
- `409 CONFLICT` — `NAME_CONFLICT`
- `422 Unprocessable Entity` — `SCHEMA_INFERENCE_FAILED`
- `400 Bad Request` — invalid `name`/`deps`/`notebook`

---

### Get by name
`GET /functions/{name}` → full stored doc (incl. inferred schemas).

### List / search (lightweight)
`GET /functions`  
Query params: `q` (text search), `limit` (<=100), `cursor` (opaque), `includeNotebook` (false by default).  
Response:
```json
{
  "items": [ /* function docs (omit notebook by default) */ ],
  "nextCursor": "opaque-token-if-any"
}
```

### Update (replace notebook/description/deps)
`PUT /functions/{name}`

**Request body** (any subset of editable fields):
```json
{
  "description": "…",
  "deps": ["pandas==2.2.2"],
  "notebook": { /* ipynb JSON */ }
}
```
If `notebook` is present → re-infer schemas.

**Responses**: `200 OK`, `404 NOT_FOUND`, `422 SCHEMA_INFERENCE_FAILED`.

### Delete
`DELETE /functions/{name}` → `204 No Content` (hard delete for first cut).

### Dry-run schema inference (no persist)
`POST /functions:preview`
```json
{ "deps": ["pydantic==2.8.0"], "notebook": { /* ipynb JSON */ } }
```
**Response**
```json
{ "inputSchema": {...}, "outputSchema": {...} }
```

---

## 5) OpenAPI (condensed)

```yaml
openapi: 3.0.3
info:
  title: Function Registry (Minimal)
  version: 0.1.0
paths:
  /api/registry/functions:
    get:
      parameters:
        - in: query
          name: q
          schema: { type: string }
        - in: query
          name: limit
          schema: { type: integer, maximum: 100 }
        - in: query
          name: cursor
          schema: { type: string }
        - in: query
          name: includeNotebook
          schema: { type: boolean, default: false }
      responses:
        "200":
          description: OK
    post:
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/FunctionCreate' }
      responses:
        "201": { description: Created }
        "409": { description: Conflict }
        "422": { description: Schema inference failed }
  /api/registry/functions/{name}:
    get:
      parameters:
        - in: path
          name: name
          required: true
          schema: { type: string }
      responses:
        "200": { description: OK }
        "404": { description: Not Found }
    put:
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/FunctionUpdate' }
      responses:
        "200": { description: OK }
        "404": { description: Not Found }
        "422": { description: Schema inference failed }
    delete:
      responses:
        "204": { description: No Content }
        "404": { description: Not Found }
  /api/registry/functions:preview:
    post:
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/FunctionPreview' }
      responses:
        "200": { description: OK }
        "422": { description: Schema inference failed }
components:
  schemas:
    FunctionDoc:
      type: object
      required: [name, description, runtime, deps, notebook, inputSchema, outputSchema, createdAt, updatedAt]
      properties:
        _id: { type: string }
        name: { type: string, pattern: '^[a-zA-Z_][a-zA-Z0-9_-]{2,64}$' }
        description: { type: string }
        runtime: { type: object, properties: { kind: { type: string, enum: [python] } } }
        deps: { type: array, items: { type: string } }
        notebook: { type: object }
        inputSchema: { type: object }
        outputSchema: { type: object }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
    FunctionCreate:
      type: object
      required: [name, description, deps, notebook]
      properties:
        name: { type: string }
        description: { type: string }
        deps: { type: array, items: { type: string } }
        notebook: { type: object }
    FunctionUpdate:
      type: object
      properties:
        description: { type: string }
        deps: { type: array, items: { type: string } }
        notebook: { type: object }
    FunctionPreview:
      type: object
      required: [deps, notebook]
      properties:
        deps: { type: array, items: { type: string } }
        notebook: { type: object }
```

---

## 6) Example notebook contracts

### Preferred (Pydantic)
```python
from pydantic import BaseModel
from typing import Dict, List

class Params(BaseModel):
    baseline: Dict[str, float]
    ai_samples: Dict[str, List[float]]
    total_users: int
    ai_user_count: int
    target_service: str
    growth_value: float
    growth_mode: str | None = "percent_of_total"

class Output(BaseModel):
    simulated_avg: Dict[str, float]
    delta_wtp: Dict[str, float]
    added_adopters: int

def run(params: Params) -> Output:
    # ... compute ...
    return Output(simulated_avg=..., delta_wtp=..., added_adopters=...)
```

### Fallback (schema cell)
```python
INPUT_SCHEMA = { "type":"object", "required":["target_service","growth_value"], "properties": { ... } }
OUTPUT_SCHEMA = { "type":"object", "required":["simulated_avg","delta_wtp","added_adopters"], "properties": { ... } }

def run(params):
    # ... compute ...
    return {"simulated_avg": {...}, "delta_wtp": {...}, "added_adopters": 40}
```

---

## 7) Error codes

```json
{ "code":"NAME_CONFLICT", "message":"Function 'simulate_service_uplift' already exists." }
{ "code":"INVALID_NOTEBOOK", "message":"nbformat must be >=4; missing cells." }
{ "code":"SCHEMA_INFERENCE_FAILED", "message":"Could not infer schemas.", "hints":["Add Pydantic Params/Output","or provide INPUT_SCHEMA/OUTPUT_SCHEMA cell"] }
```
