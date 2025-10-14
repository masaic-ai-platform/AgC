package tools.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.ClientSideTool;
import tools.ToolRequest;

public class AddNumbersTool implements ClientSideTool {
    private static final Logger logger = LoggerFactory.getLogger(AddNumbersTool.class);
    private static String TOOL_NAME = "add_two_numbers";
    private static String PROFILE_ID = "user_yWrOnKu6n";

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
