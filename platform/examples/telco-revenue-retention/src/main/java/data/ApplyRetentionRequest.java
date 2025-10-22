package data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request for ApplyRetentionActions tool
 */
public class ApplyRetentionRequest {
    
    public String action;
    public List<CustomerRequest> customers;

    public ApplyRetentionRequest() {}

    public ApplyRetentionRequest(String action, List<CustomerRequest> customers) {
        this.action = action;
        this.customers = customers;
    }

    /**
     * Individual customer request within retention action
     */
    public static class CustomerRequest {
        @JsonProperty("customer_id_hash")
        public String customer_id_hash;

        public CustomerRequest() {}

        public CustomerRequest(String customer_id_hash) {
            this.customer_id_hash = customer_id_hash;
        }
    }
}
