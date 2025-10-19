package usecases.telcoRevenueRetention.core.repository;

import usecases.telcoRevenueRetention.data.ApplyRetentionRequest;
import usecases.telcoRevenueRetention.data.ApplyRetentionResponse;

import java.util.List;

/**
 * Repository for managing retention action data operations
 */
public class RetentionActionRepository {
    
    /**
     * Process retention actions for customers
     * @param request The retention action request
     * @return List of retention action results
     */
    public List<ApplyRetentionResponse.RetentionActionResult> processRetentionActions(ApplyRetentionRequest request) {
        // For now, we'll simulate the processing logic
        return processRetentionActionsInternal(request);
    }
    
    /**
     * Check if a customer is valid for retention actions
     * @param customerIdHash The customer ID hash
     * @return true if customer is valid, false otherwise
     */
    public boolean isValidCustomer(String customerIdHash) {
        if (customerIdHash == null || customerIdHash.trim().isEmpty()) {
            return false;
        }
        if (customerIdHash.equals("invalid_customer") || customerIdHash.length() < 8) {
            return false;
        }
        return true;
    }
    
    /**
     * Check if an action type is supported
     * @param action The action type
     * @return true if action is supported, false otherwise
     */
    public boolean isSupportedAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            return false;
        }
        return switch (action) {
            case "assign_agent", "send_retention_offer", "schedule_callback", "waive_fee" -> true;
            default -> false;
        };
    }
    
    /**
     * Generate a unique case reference hash
     * @return A unique case reference hash
     */
    public String generateCaseRefHash() {
        return "CASE_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
    
    private List<ApplyRetentionResponse.RetentionActionResult> processRetentionActionsInternal(ApplyRetentionRequest request) {
        List<ApplyRetentionResponse.RetentionActionResult> results = new java.util.ArrayList<>();
        if (request.customers == null || request.customers.isEmpty()) {
            return results;
        }
        for (ApplyRetentionRequest.CustomerRequest customer : request.customers) {
            try {
                String status = processCustomerRetentionAction(customer.customer_id_hash, request.action);
                if ("created".equals(status)) {
                    String caseRefHash = generateCaseRefHash();
                    results.add(new ApplyRetentionResponse.RetentionActionResult(
                        customer.customer_id_hash, 
                        "created", 
                        caseRefHash, 
                        null, 
                        null
                    ));
                } else {
                    results.add(new ApplyRetentionResponse.RetentionActionResult(
                        customer.customer_id_hash, 
                        "failed", 
                        null, 
                        "PROCESSING_ERROR", 
                        "Failed to process retention action"
                    ));
                }
            } catch (Exception ex) {
                results.add(new ApplyRetentionResponse.RetentionActionResult(
                    customer.customer_id_hash, 
                    "failed", 
                    null, 
                    "PROCESSING_ERROR", 
                    ex.getMessage()
                ));
            }
        }
        return results;
    }
    
    private String processCustomerRetentionAction(String customerIdHash, String action) {
        if (!isValidCustomer(customerIdHash)) {
            return "failed";
        }
        if (!isSupportedAction(action)) {
            return "failed";
        }
        return "created";
    }
}
