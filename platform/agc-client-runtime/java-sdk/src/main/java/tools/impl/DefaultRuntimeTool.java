package tools.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.AgcRuntimeTool;
import tools.ToolRequest;

public class DefaultRuntimeTool implements AgcRuntimeTool {
    private static final Logger logger = LoggerFactory.getLogger(DefaultRuntimeTool.class);
    private static String TOOL_NAME = "default";
    private static String PROFILE_ID = "default";

    @Override
    public String toolId() {
        return PROFILE_ID + "." + TOOL_NAME;
    }

    @Override
    public String executeTool(ToolRequest request) {
        logger.info("Executing AddNumbersTool: name={}, arguments={}, loopContextInfo={}",
                request != null ? request.getName() : null,
                request != null ? request.getArguments() : null,
                request != null ? request.getLoopContextInfo() : null);
        return "success";
    }
}
