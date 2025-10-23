package tools;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgcRuntimeTool {
    String toolId();

    @ActivityMethod
    String executeTool(ToolRequest request);
}
