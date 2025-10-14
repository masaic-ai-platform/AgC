package tools.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.ClientSideTool;

public class AddNumbersTool implements ClientSideTool {
    private static final Logger logger = LoggerFactory.getLogger(AddNumbersTool.class);
    private static String TOOL_NAME = "add_two_numbers";
    private static String PROFILE_ID = "user_yWrOnKu6n";

    @Override
    public String toolId() {
        return PROFILE_ID + "." + TOOL_NAME;
    }

    @Override
    public String executeTool(String argumentsJson) {
        logger.debug("Executing AddNumbersTool with arguments: {}", argumentsJson);
        //implement your code....
        return "";
    }
}
