package ai.masaic.platform.api.controller

import ai.masaic.openresponses.tool.ToolService
import ai.masaic.openresponses.tool.mcp.MCPToolExecutor
import ai.masaic.platform.api.interpreter.CodeExecResult
import ai.masaic.platform.api.interpreter.CodeExecuteReq
import ai.masaic.platform.api.interpreter.CodeRunnerService
import ai.masaic.platform.api.registry.functions.*
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * REST controller for function registry operations.
 * Provides CRUD operations for compute functions.
 */
@Profile("platform")
@RestController
@RequestMapping("/api/registry")
@CrossOrigin("*")
class FunctionRegistryController(
    private val functionRegistryService: FunctionRegistryService,
    private val toolService: ToolService,
    private val mcpToolExecutor: MCPToolExecutor,
    private val codeRunnerService: CodeRunnerService
) {

    /**
     * Create a new function.
     * POST /api/registry/functions
     */
    @PostMapping("/functions")
    suspend fun createFunction(@RequestBody request: FunctionCreate): ResponseEntity<FunctionDoc> {
        return try {
            val function = functionRegistryService.createFunction(request)
            ResponseEntity.status(HttpStatus.CREATED).body(function)
        } catch (e: FunctionRegistryException) {
            when (e.code) {
                ErrorCodes.NAME_CONFLICT -> throw ResponseStatusException(HttpStatus.CONFLICT, e.message)
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
            }
        }
    }

    /**
     * Get a function by name.
     * GET /api/registry/functions/{name}
     */
    @GetMapping("/functions/{name}")
    suspend fun getFunction(@PathVariable name: String): ResponseEntity<FunctionDoc> {
        return try {
            val function = functionRegistryService.getFunction(name)
            ResponseEntity.ok(function)
        } catch (e: FunctionRegistryException) {
            when (e.code) {
                ErrorCodes.FUNCTION_NOT_FOUND -> throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
            }
        }
    }

    /**
     * List functions with optional search and pagination.
     * GET /api/registry/functions
     */
    @GetMapping("/functions")
    suspend fun listFunctions(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "false") includeCode: Boolean
    ): ResponseEntity<FunctionListResponse> {
        // Validate limit parameter
        if (limit > 100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit cannot exceed 100")
        }

        return try {
            val response = functionRegistryService.listFunctions(
                query = q,
                limit = limit,
                cursor = cursor,
                includeCode = includeCode
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to list functions")
        }
    }

    /**
     * Update a function.
     * PUT /api/registry/functions/{name}
     */
    @PutMapping("/functions/{name}")
    suspend fun updateFunction(
        @PathVariable name: String,
        @RequestBody request: FunctionUpdate
    ): ResponseEntity<FunctionDoc> {
        return try {
            val function = functionRegistryService.updateFunction(name, request)
            ResponseEntity.ok(function)
        } catch (e: FunctionRegistryException) {
            when (e.code) {
                ErrorCodes.FUNCTION_NOT_FOUND -> throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
            }
        }
    }

    /**
     * Delete a function.
     * DELETE /api/registry/functions/{name}
     */
    @DeleteMapping("/functions/{name}")
    suspend fun deleteFunction(@PathVariable name: String): ResponseEntity<Unit> {
        return try {
            functionRegistryService.deleteFunction(name)
            ResponseEntity.noContent().build()
        } catch (e: FunctionRegistryException) {
            when (e.code) {
                ErrorCodes.FUNCTION_NOT_FOUND -> throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
            }
        }
    }

    @PostMapping("/functions/{name}/execute")
    suspend fun executeFunction(@PathVariable name: String, @RequestBody funExecuteReq: CodeExecuteReq): ResponseEntity<CodeExecResult> {
        return ResponseEntity.ok(codeRunnerService.runCode(funExecuteReq.copy(funName = name)))
    }

    /**
     * Preview function (placeholder for future schema inference).
     * POST /api/registry/functions:preview
     */
    @PostMapping("/functions:preview")
    suspend fun previewFunction(@RequestBody request: FunctionPreview): ResponseEntity<Map<String, Any>> {
        return try {
            val schemas = functionRegistryService.previewFunction(request)
            ResponseEntity.ok(schemas)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to preview function")
        }
    }
}
