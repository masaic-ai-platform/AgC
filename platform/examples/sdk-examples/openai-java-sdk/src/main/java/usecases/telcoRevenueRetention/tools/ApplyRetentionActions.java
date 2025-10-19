package usecases.telcoRevenueRetention.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import common.AgcRuntimeTool;
import common.ToolRequest;
import usecases.telcoRevenueRetention.core.service.RetentionActionService;
import usecases.telcoRevenueRetention.data.ApplyRetentionRequest;
import usecases.telcoRevenueRetention.data.ApplyRetentionResponse;

public class ApplyRetentionActions implements AgcRuntimeTool {
    private static final Logger logger = LoggerFactory.getLogger(ApplyRetentionActions.class);
    private static String TOOL_NAME = "apply_retention_actions";
    private static String PROFILE_ID = "user_yWrOnKu6n";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RetentionActionService retentionActionService;

    public ApplyRetentionActions() {
        this.retentionActionService = new RetentionActionService();
    }

    public ApplyRetentionActions(RetentionActionService retentionActionService) {
        this.retentionActionService = retentionActionService;
    }

    @Override
    public String toolId() {
        return PROFILE_ID + "." + TOOL_NAME;
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
