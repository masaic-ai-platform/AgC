package ai.masaic.temporal

import ai.masaic.openresponses.tool.PluggedToolRequest
import io.temporal.activity.ActivityInterface

@ActivityInterface
interface ClientSideToolExecutionActivity {
    fun executeTool(pluggedToolRequest: PluggedToolRequest): String
}
