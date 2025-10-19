package usecases.telcoRevenueRetention.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.AgcRuntimeTool;
import common.ToolRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import usecases.telcoRevenueRetention.core.service.CohortSelectorService;
import usecases.telcoRevenueRetention.data.RetentionCohortRequest;
import usecases.telcoRevenueRetention.data.RetentionCohortResponse;

import java.util.List;

public class SelectRetentionCohort implements AgcRuntimeTool {
    private static final Logger logger = LoggerFactory.getLogger(SelectRetentionCohort.class);
    private static String TOOL_NAME = "select_retention_cohort";
    private static String PROFILE_ID = "user_yWrOnKu6n";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CohortSelectorService cohortSelectorService;

    public SelectRetentionCohort() {
        this.cohortSelectorService = new CohortSelectorService();
    }

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
        try {
            if (request == null || request.getArguments() == null || request.getArguments().isBlank()) {
                return "{\"error\":\"Bad_Request\",\"message\":\"No arguments provided. Please provide required parameters.\"}";
            }
            RetentionCohortRequest p = MAPPER.readValue(request.getArguments(), RetentionCohortRequest.class);
            List<RetentionCohortResponse.CohortItem> cohort = cohortSelectorService.select(p);
            // Applying the risk scores and create response
            List<RetentionCohortResponse.CohortItem> roundedCohort = cohort.stream()
                    .map(item -> new RetentionCohortResponse.CohortItem(item.id_hash, round3(item.risk_score)))
                    .collect(java.util.stream.Collectors.toList());
            RetentionCohortResponse response = new RetentionCohortResponse(roundedCohort);
            return MAPPER.writeValueAsString(response);
        } catch (Exception ex) {
            logger.error("Exception occur {}", ex.getMessage());
            return "{\"error\":\"" + "Internal_Server_Error" + "\",\"message\":\"" + ex.getMessage() + "\"}";
        }
    }

    public double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}













