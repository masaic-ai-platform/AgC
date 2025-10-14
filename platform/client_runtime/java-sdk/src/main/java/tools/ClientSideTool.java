package tools;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ClientSideTool {
    String toolId();

    @ActivityMethod
    String executeTool(String argumentsJson);
}
