package tools;

public interface ClientSideTool {
    String toolId();
    String executeTool(String argumentsJson);
}
