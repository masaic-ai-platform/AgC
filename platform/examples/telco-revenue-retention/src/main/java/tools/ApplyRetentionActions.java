package tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.service.RetentionActionService;
import data.ApplyRetentionRequest;
import data.ApplyRetentionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import common.AgCClientSideTool;
import common.ToolRequest;


public class ApplyRetentionActions implements AgCClientSideTool {
    private static final Logger logger = LoggerFactory.getLogger(ApplyRetentionActions.class);
    private static String TOOL_NAME = "apply_retention_actions";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RetentionActionService retentionActionService;

    public ApplyRetentionActions() {
        this.retentionActionService = new RetentionActionService();
    }
    @Override
    public String toolId() {
        return TOOL_NAME;
    }

    @Override
    public String executeTool(ToolRequest request) {
        logger.info("Executing ApplyRetentionActions: name={}, arguments={}, loopContextInfo={}",
                request != null ? request.getName() : null,
                request != null ? request.getArguments() : null,
                request != null ? request.getLoopContextInfo() : null);
        try {
            if (request == null || request.getArguments() == null || request.getArguments().isBlank()) {
                return "{\"error\":\"Bad_Request\",\"message\":\"No arguments provided. Please provide required parameters.\"}";
            }
            ApplyRetentionRequest actionRequest = MAPPER.readValue(request.getArguments(), ApplyRetentionRequest.class);
            // Process retention actions through service
            ApplyRetentionResponse response = retentionActionService.applyRetentionActions(actionRequest);
            return MAPPER.writeValueAsString(response);
        } catch (Exception ex) {
            logger.error("Exception occurred in ApplyRetentionActions: {}", ex.getMessage());
            return "{\"error\":\"Internal_Server_Error\",\"message\":\"" + ex.getMessage() + "\"}";
        }
    }
}
