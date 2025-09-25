package ai.masaic.platform.api.controller

import ai.masaic.openresponses.api.model.CreateCompletionRequest
import ai.masaic.openresponses.api.model.FunctionTool
import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.openresponses.api.model.ModelInfo
import ai.masaic.openresponses.api.service.ResponseProcessingException
import ai.masaic.openresponses.tool.ToolService
import ai.masaic.openresponses.tool.mcp.*
import ai.masaic.openresponses.tool.mcp.oauth.MCPOAuthService
import ai.masaic.platform.api.config.ModelSettings
import ai.masaic.platform.api.config.PlatformCoreConfig
import ai.masaic.platform.api.config.PlatformInfo
import ai.masaic.platform.api.interpreter.CodeExecResult
import ai.masaic.platform.api.interpreter.CodeExecuteReq
import ai.masaic.platform.api.interpreter.CodeRunnerService
import ai.masaic.platform.api.model.*
import ai.masaic.platform.api.registry.functions.FunctionRegistryService
import ai.masaic.platform.api.service.ModelService
import ai.masaic.platform.api.service.createCompletion
import ai.masaic.platform.api.service.messages
import ai.masaic.platform.api.tools.FunDefGenerationTool
import ai.masaic.platform.api.tools.SystemPromptGeneratorTool
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.*

@Profile("platform")
@RestController
@RequestMapping("/v1/dashboard")
@CrossOrigin("*")
class DashboardController(
    private val toolService: ToolService,
    private val mcpToolExecutor: MCPToolExecutor,
    private val modelService: ModelService,
    private val modelSettings: ModelSettings,
    private val funDefGenerationTool: FunDefGenerationTool,
    private val platformInfo: PlatformInfo,
    private val mcpToolRegistry: MCPToolRegistry,
    private val codeRunnerService: CodeRunnerService,
    private val systemPromptGeneratorTool: SystemPromptGeneratorTool,
    private val functionRegistryService: FunctionRegistryService,
    private val mcpoAuthService: MCPOAuthService,
) {
    private val mapper = jacksonObjectMapper()
    private val modelProviders: Set<ModelProvider> = PlatformCoreConfig.loadProviders()
    private val log = KotlinLogging.logger { }

    @GetMapping("/models", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getModelProviders(): ResponseEntity<Set<ModelProvider>> = ResponseEntity.ok(modelProviders)

    @PostMapping("/generate/schema", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun generateSchema(
        @RequestBody request: SchemaGenerationRequest,
        @RequestHeader("Authorization") authHeader: String? = null,
    ): ResponseEntity<SchemaGenerationResponse> {
        val generateSchemaPrompt =
            """
You are an expert JSON Schema generator.

TASK  
• Read the incoming plain-language block (marked “SCHEMA DESCRIPTION”).  
• Produce **exactly one** JSON object with these top-level keys, in this order:  
  1. "name"– kebab-case or snake_case identifier for the schema you infer.  
  2. "schema"– a valid Draft-07 JSON Schema describing the response.  
  3. "strict"– the literal boolean true.

RULES FOR "schema" VALUE  
• Must be an object containing "type", "properties", "re    quired", and "additionalProperties".  
• Infer property names, types, and per-property "description" strings from the description text.  
• Include every property mentioned; list them all under "required".  
• Set `"additionalProperties": false`.  
• Follow the JSON-Schema spec at https://json-schema.org/ (Draft-07 defaults).  
• Do **not** add any keys not defined by the spec.

OUTPUT FORMAT  
```json
{
  "name": "<inferred_name>",
  "schema": {
    ...draft-07 schema here...
  },
  "strict": true
}

CONSTRAINTS
• Return pure JSON – no markdown fences, no comments, no extra text.
• Preserve camelCase, snake_case, or kebab-case exactly as given in the description.
• If the description mentions enums, arrays, nested objects, or numeric ranges, map them to the appropriate JSON-Schema constructs.

SCHEMA DESCRIPTION
${request.description}
            """.trimIndent()

        val applicableSettings = modelSettings.resolveSystemSettings(ModelInfo.fromApiKey(authHeader, request.modelInfo?.model))
        val createCompletionRequest =
            CreateCompletionRequest(
                messages =
                    messages {
                        systemMessage(generateSchemaPrompt)
                        userMessage("Generate Json Schema")
                    },
                model = applicableSettings.qualifiedModelName,
                stream = false,
                store = false,
            )

        val response: String = modelService.fetchCompletionPayload(createCompletionRequest, applicableSettings.apiKey)
        return ResponseEntity.ok(SchemaGenerationResponse(response))
    }

    @PostMapping("/generate/function", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun generateFunction(
        @RequestBody request: FunctionGenerationRequest,
        @RequestHeader("Authorization") authHeader: String? = null,
    ): ResponseEntity<FunctionGenerationResponse> {
        val finalSettings = modelSettings.resolveSystemSettings(ModelInfo.fromApiKey(authHeader, request.modelInfo?.model))
        val response = funDefGenerationTool.executeTool(request, finalSettings)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/generate/prompt", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun generatePrompt(
        @RequestBody request: PromptGenerationRequest,
        @RequestHeader("Authorization") authHeader: String? = null,
    ): ResponseEntity<PromptGenerationResponse> {
        val finalSettings = modelSettings.resolveSystemSettings(ModelInfo.fromApiKey(authHeader, request.modelInfo?.model))
        val systemPrompt = systemPromptGeneratorTool.generatePrompt(request, finalSettings)
        return ResponseEntity.ok(PromptGenerationResponse(systemPrompt))
    }

    @PostMapping("/mcp/list_actions")
    suspend fun listMcpActions(
        @RequestBody mcpListToolsRequest: McpListToolsRequest,
    ): ResponseEntity<*> {
        val mcpTool =
            MCPTool(
                type = "mcp",
                serverLabel = mcpListToolsRequest.serverLabel,
                serverUrl = mcpListToolsRequest.serverUrl,
                headers = mcpListToolsRequest.headers,
            )

        var beginOAuthFlow = false
        if (mcpListToolsRequest.isOAuth) {
            try {
                val accessToken = mcpoAuthService.ensureFreshAccessToken(mcpTool)
                return ResponseEntity.ok(geTools(mcpListToolsRequest.copy(headers = mapOf("Authorization" to "Bearer $accessToken"))))
            } catch (e: McpUnAuthorizedException) {
                log.info { "Token not found for mcp server=${mcpTool.serverUrl}, initiating oAuth flow." }
                beginOAuthFlow = true
            }
        }

        if (beginOAuthFlow) {
            val redirectUri = URI("${platformInfo.oAuthRedirectSpecs.agcPlatformRedirectUri}/v1/dashboard/oauth/callback")
            log.info { "Redirect URI for oAuth login will be $redirectUri" }
            val oAuthUri = mcpoAuthService.beginOAuthFlow(mcpTool, redirectUri)
            log.info { "oAuthUri for mcp is $oAuthUri" }
            return ResponseEntity.ok().body(mapOf("statusCode" to HttpStatus.UNAUTHORIZED, "location" to oAuthUri.toString()))
        }

        return ResponseEntity.ok(geTools(mcpListToolsRequest))
    }

    private suspend fun geTools(mcpListToolsRequest: McpListToolsRequest): List<FunctionTool> {
        val mcpTool =
            MCPTool(
                type = "mcp",
                serverLabel = mcpListToolsRequest.serverLabel,
                serverUrl = mcpListToolsRequest.serverUrl,
                headers = mcpListToolsRequest.headers,
            )
        val tools =
            toolService.getRemoteMcpTools(mcpTool)

        val updatedTools =
            tools.map {
                it.copy(name = it.name?.replace("${mcpListToolsRequest.serverLabel}_", ""))
            }

        mcpListToolsRequest.testMcpTool.forEach {
            val mcpServerInfo = MCPServerInfo(mcpListToolsRequest.serverLabel, mcpListToolsRequest.serverUrl)
            try {
                executeMCPTool(it.copy(name = mcpServerInfo.qualifiedToolName(it.name)))
            } catch (e: ResponseProcessingException) {
                mcpToolRegistry.removeMcpServer(mcpServerInfo)
                throw e
            }
        }
        return updatedTools
    }

    @GetMapping("/oauth/callback")
    suspend fun callback(
        @RequestParam code: String,
        @RequestParam state: String,
    ): ResponseEntity<Any> {
        val mcpTool = mcpoAuthService.handleCallback(code, state)
        val finalUrl = "${platformInfo.oAuthRedirectSpecs.agcUiHost}?screen=playground&modal=mcp&serverUrl=${mcpTool.serverUrl}&serverLabel=${mcpTool.serverLabel}&accessToken=${mcpTool.headers["accessToken"]}"
        log.info { "finalUrl: $finalUrl" }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI(finalUrl)).build()
    }

    @PostMapping("/tools/mcp/execute", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun executeMCPTool(
        @RequestBody toolRequest: ExecuteToolRequest,
    ): String {
        val toolDefinition = toolService.findToolByName(toolRequest.name) ?: throw ResponseProcessingException("Tool ${toolRequest.name} not found.")
        val toolResponse =
            try {
                mcpToolExecutor.executeTool(toolDefinition, mapper.writeValueAsString(toolRequest.arguments), null, null) ?: throw ResponseProcessingException("no response returned by tool ${toolRequest.name}")
            } catch (ex: McpUnAuthorizedException) {
                mcpToolRegistry.invalidateTool(toolDefinition as McpToolDefinition)
                log.error("Received ${ex.javaClass}, while running ${toolDefinition.name}, error: ${ex.message}")
                throw ex
            }

        val callToolResponse: CallToolResponse = mapper.readValue(toolResponse)
        return if (callToolResponse.isError) throw ResponseProcessingException(callToolResponse.content) else toolResponse
    }

    @GetMapping("/platform/info", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPlatformInfo() = platformInfo

    @PostMapping("/agc/functions:suggest")
    suspend fun configureFunction(
        @RequestBody request: SuggestPyFunDetailsRequest,
        @RequestHeader("Authorization") authHeader: String? = null,
    ): ResponseEntity<SuggestPyFunDetailsResponse> {
        val suggestionsPrompt =
            """
Code Validation Prompt (Simplified)

You are a Python code validation assistant.
You will be given only Python code as input.
Analyze it and return a JSON object in the following structure:

{
  "suggestedFunctionDetails": {
    "name": "string",
    "description": "string",
    "parameters": { }   // valid JSON Schema for function parameters
  },
  "testData": { },       // example JSON test data to call the function
  "isCodeValid": true,   // or false
  "codeProblem": "string or null"
}


⸻

Rules
	1.	Validation
	•	Ensure the input consists only of valid Python code lines.
	•	If syntax errors exist → return "isCodeValid": false and set "codeProblem" with the error.
	•	If the code is not Python at all → return "isCodeValid": false and "codeProblem": "Not valid Python code".
	2.	Pandas Restriction
	•	Code may use Pandas internally.
	•	But if any function parameter is of type pandas.DataFrame, mark invalid:

"isCodeValid": false,
"codeProblem": "Function parameters cannot use Pandas DataFrame. Use dicts or lists instead."


	3.	Function Metadata Extraction
	•	Extract:
	•	name: the function name. It should be exactly same as the entrypoint function defined in the code.   
	•	description: meaningful one-liner based on docstring or code purpose.
	•	parameters: generate JSON Schema (https://json-schema.org/) for function parameters.
	•	If no function is defined → "isCodeValid": false, "codeProblem": "No function definition found".
	4.	Test Data
	•	Generate a small JSON example input matching the parameter schema.
	•	If no valid parameters → "testData": null.

⸻

Example Input

def add(a: int, b: int) -> int:
    return a + b

Example Output

{
  "suggestedFunctionDetails": {
    "name": "add",
    "description": "Adds two integers and returns the sum.",
    "parameters": {
      "type": "object",
      "properties": {
        "a": { "type": "integer" },
        "b": { "type": "integer" }
      },
      "required": ["a", "b"]
    }
  },
  "testData": { "a": 10, "b": 20 },
  "isCodeValid": true,
  "codeProblem": null
}
            """.trimIndent()

        val userMessage =
            """
Code:
${String(Base64.getDecoder().decode(request.encodedCode), charset = Charsets.UTF_8)}
            """.trimIndent()

        val finalSettings = modelSettings.resolveSystemSettings(ModelInfo.fromApiKey(authHeader, request.modelInfo?.model))
        val createCompletionRequest =
            CreateCompletionRequest(
                messages =
                    messages {
                        systemMessage(suggestionsPrompt)
                        userMessage(userMessage)
                    },
                model = finalSettings.qualifiedModelName,
                stream = false,
                store = false,
            )
        var response: SuggestPyFunDetailsResponse? = null
        try {
            response = modelService.createCompletion<SuggestPyFunDetailsResponse>(createCompletionRequest, finalSettings.apiKey)
        } catch (ex: Exception) {
            throw ResponseProcessingException("Unable to create suggestions due to error ${ex.message}")
        }

        val functionDetails = request.functionDetails
        val suggestedFunctionResponse = response.suggestedFunctionDetails
        val finalSuggestedFunDetails = ConfigureFunDetails(name = suggestedFunctionResponse?.name, description = functionDetails?.description ?: suggestedFunctionResponse?.description, parameters = suggestedFunctionResponse?.parameters ?: functionDetails?.parameters)
        val finalResponse = response.copy(suggestedFunctionDetails = finalSuggestedFunDetails, testData = response.testData ?: request.testData)

        val funName = finalResponse.suggestedFunctionDetails?.name ?: "not available"
        return ResponseEntity.ok(finalResponse)
    }

    @PostMapping("/functions/{name}:execute", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun executeFunction(
        @PathVariable name: String,
        @RequestBody funExecuteReq: CodeExecuteReq,
    ): ResponseEntity<CodeExecResult> =
        ResponseEntity.ok(
            codeRunnerService.runCode(
                funExecuteReq.copy(funName = name, encodedCode = funExecuteReq.encodedCode),
                eventEmitter = { },
            ),
        )

    @GetMapping("/functions:names", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun listFunctionNames(): ResponseEntity<List<String>> {
        val response =
            functionRegistryService.listFunctions(
                query = null,
                limit = 1000,
                cursor = null,
                includeCode = false,
            )
        return ResponseEntity.ok(response.items.map { it.name })
    }

    @GetMapping("/functions/{funName}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun getFunctionDetails(
        @PathVariable funName: String,
    ): ResponseEntity<SuggestPyFunDetailsResponse> {
        val response =
            functionRegistryService.getFunction(name = funName)
        val funDetails =
            SuggestPyFunDetailsResponse(
                suggestedFunctionDetails =
                    ConfigureFunDetails(
                        name = response.name,
                        description = response.description,
                        parameters = response.inputSchema,
                    ),
                isCodeValid = true,
                code = response.code,
            )
        return ResponseEntity.ok(funDetails)
    }
}

data class ExecuteToolRequest(
    val name: String,
    val arguments: Map<String, Any>,
)

data class SuggestPyFunDetailsRequest(
    val encodedCode: String,
    val functionDetails: ConfigureFunDetails? = null,
    val testData: MutableMap<String, Any>? = null,
    val modelInfo: ModelInfo?,
)

data class ConfigureFunDetails(
    val name: String? = null,
    val description: String? = null,
    val parameters: MutableMap<String, Any>? = null,
)

data class SuggestPyFunDetailsResponse(
    val suggestedFunctionDetails: ConfigureFunDetails? = null,
    val testData: MutableMap<String, Any>? = null,
    val isCodeValid: Boolean,
    val codeProblem: String? = null,
    val code: String? = null,
)
