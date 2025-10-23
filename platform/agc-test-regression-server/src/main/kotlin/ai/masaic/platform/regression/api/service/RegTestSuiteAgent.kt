package ai.masaic.platform.regression.api.service

import ai.masaic.openresponses.api.model.MCPTool
import ai.masaic.platform.api.model.PlatformAgent
import ai.masaic.platform.regression.api.tools.RegSuiteMcpClient

class RegTestSuiteAgent {
    fun getAgent(): PlatformAgent =
        PlatformAgent(
            name = "Reg-Test-Suite-Agent",
            description = "Executes Playwright test scripts, collects steps trail, applies user-provided assertions, and reports assertion pass/fail status with summaries. Prompts for missing script name or assertions as needed.",
            systemPrompt = agentPrompt,
            userMessage = "Tell me the weather of San Francisco",
            model = "openai@gpt-4.1-mini",
            tools = listOf(MCPTool(type = "mcp", serverLabel = "reg-mcp-server", serverUrl = RegSuiteMcpClient.REGRESS_SERVER_URL, allowedTools = listOf(RegSuiteMcpClient.RUN_PW_SCRIPT_TOOL_NAME, RegSuiteMcpClient.GET_TEST_TRAIL_TOOL_NAME))),
            suggestedQueries = listOf(allBirdsMCPTest, addModelTest, fileSearchTest, agentBuilderTest, ecommerceAgentTest, mockyTest, mcpOauthConTest),
        )

    private val agentPrompt =
        """
Automated Playwright Test Runner Agent

- Accept and validate the following inputs: Playwright script name and a list of assertions to verify on the test steps trail. 
- Use the run_play_wright_script tool to execute the specified Playwright script.
- If the script execution returns an error or failure, immediately declare the test failed and halt further steps.
- If the script executes successfully, extract the responseId and use the get_test_steps_trail tool to retrieve the executed test steps.
- Compare the user-provided assertions against the actual test steps trail. The following are the guidelines for assertion:
1. Tools: if a tool is executed then test trail would have an items indicating tool arguments and tool result.
2. Output: response items of assistant contain details mentioned in output assertions.
- Generate a concise report:
  - Provide a summary table showing each assertion and whether it passed or failed.
  - Follow with a detailed bulleted summary (no more than 200 words), explaining which assertions passed or failed and why, including root causes for any failures.

## Example test trail for reference
[
  {
    "user": [
      "<user message>" //this is a message to assistant from user
    ]
  },
  {
    "assistant": { //this is a tool call for sample_tool 
      "type": "tool_call",
      "name": "sample_tool",
      "arguments": "<json of tool arguments>",
      "call_id": "sample_call_id"
    }
  },
  {
    "tool": { //this is output of sample_too, tool call and output mapped by common sample_call_id
      "output": "<result of the tool called by too_call and call_id",
      "call_id": "sample_call_id"
    }
  },
  {
    "assistant": [
      "<message from assitant for user." //this is assistant message for user
    ]
  }
]

Output format:
- Table: | Assertion | Pass/Fail |
- Detailed summary: Bulleted list (≤200 words) explaining results.

Examples:

Input:
Script name: "login_flow"
Assertions: ["User reaches dashboard", "Error message not present"]
Output:
| Assertion                  | Pass    |
|----------------------------|---------|
| User reaches dashboard     | Pass    |
| Error message not present  | Pass    |
- Both assertions were satisfied. The script's test steps included successful dashboard navigation, and no error messages appeared. Test completed as expected.

Input:
Script name: "checkout_process"
Assertions: ["Order confirmation displayed", "Payment error shown"]
Output:
| Assertion                  | Pass/Fail |
|----------------------------|-----------|
| Order confirmation displayed | Pass      |
| Payment error shown          | Fail      |
- The script completed successfully and displayed order confirmation. However, no payment error was detected in the test steps, causing that assertion to fail. Review payment validation in the script.

Reminder: Always prompt the user for any missing script name or assertions, and do not proceed with test execution until all required inputs are supplied.
        """.trimIndent()

    private val allBirdsMCPTest =
        """
Run: allbirds-ecommerce.spec.ts
Assertions:
1. Tools: search catalog and update call must be executed.
2. Output: output must contain details of the product and checkout link
        """.trimIndent()

    private val addModelTest =
        """
Run: add-gptoss-model.spec.ts
Assertions:
1. User sends message: Hi, can you greet me good morning? 
2. Assistant: Assistant replied with valid a response greeting user good morning. 
        """.trimIndent()

    private val fileSearchTest =
        """
Run: file-search.spec.ts
Assertions:
1. User sends message: What are balanced chemical equations. Describe in 100 words with two examples of Iron
2. File search tool must be executed.
3. Assistant: Assistant replied with valid a response that:
- explains balanced chemical equations 
- provides example chemical equation. 
- also give citation reference to jesc101.pdf
        """.trimIndent()

    private val agentBuilderTest =
        """
Run: agent-builder.spec.ts
Assertions:
1. User sends message: Just finished call with  Innov Corporation. Check the call transcript and provide by insights.
2. call transcript tool must be executed.
3. Assistant: Assistant replied with valid a response that provides insights about the company from the call transcript. 
        """.trimIndent()

    private val ecommerceAgentTest =
        """
Run: chat-with-ecommerce-agent.spec.ts
Assertions:
1. User sends message to find sneakers.
2. search catalog tool must be executed.
3. Assistant: Assistant replied with list of sneakers options available.
        """.trimIndent()

    private val mockyTest =
        """
Run: mocky.spec.ts
Assertions:
1. User sends message: 'Use the given tool to add two number.\nAdd +7 and +5'
2. a tool to add given numbers must be executed.
3. Assistant: Assistant replied with sum of two numbers.
        """.trimIndent()

    private val mcpOauthConTest =
        """
Run: mcp-oauth-connection.spec.ts
Assertions:
1. User sends message: 'What all pages about AgC you have... give title of each page'
2. notion tool to search pages is executed .
3. Assistant: Assistant replied with list of pages that are about AgC.
        """.trimIndent()
}
