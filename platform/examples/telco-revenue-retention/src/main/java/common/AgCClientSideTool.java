package common;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgCClientSideTool {
    String toolId();

    @ActivityMethod
    String executeTool(ToolRequest request);
}
