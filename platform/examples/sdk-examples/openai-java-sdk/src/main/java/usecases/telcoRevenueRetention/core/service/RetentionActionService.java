package usecases.telcoRevenueRetention.core.service;

import usecases.telcoRevenueRetention.core.repository.RetentionActionRepository;
import usecases.telcoRevenueRetention.data.ApplyRetentionRequest;
import usecases.telcoRevenueRetention.data.ApplyRetentionResponse;

import java.util.List;

/**
 * Service for managing retention action business logic
 */
public class RetentionActionService {
    
    private final RetentionActionRepository retentionActionRepository;
    
    public RetentionActionService() {
        this.retentionActionRepository = new RetentionActionRepository();
    }

    /**
     * Apply retention actions to customers
     * @param request The retention action request
     * @return Response containing results and summary
     */
    public ApplyRetentionResponse applyRetentionActions(ApplyRetentionRequest request) throws Exception {
        if(!validateRequest(request)){
            throw new Exception("Bad Request");
        }
        List<ApplyRetentionResponse.RetentionActionResult> results = 
            retentionActionRepository.processRetentionActions(request);
        // Calculate summary statistics
        int total = results.size();
        int created = (int) results.stream().filter(r -> "created".equals(r.status)).count();
        int failed = total - created;
        ApplyRetentionResponse.RetentionActionSummary summary = 
            new ApplyRetentionResponse.RetentionActionSummary(total, created, failed);
        return new ApplyRetentionResponse(results, summary);
    }
    
    /**
     * Validate retention action request
     * @param request The request to validate
     * @return true if valid, false otherwise
     */
    public boolean validateRequest(ApplyRetentionRequest request) {
        if (request == null) {
            return false;
        }
        
        if (request.action == null || request.action.trim().isEmpty()) {
            return false;
        }
        
        if (request.customers == null || request.customers.isEmpty()) {
            return false;
        }
        
        // Validate each customer request
        for (ApplyRetentionRequest.CustomerRequest customer : request.customers) {
            if (customer.customer_id_hash == null || customer.customer_id_hash.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
