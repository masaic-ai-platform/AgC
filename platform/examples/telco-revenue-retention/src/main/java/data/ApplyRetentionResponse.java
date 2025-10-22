package data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response for ApplyRetentionActions tool
 */
public class ApplyRetentionResponse {
    
    public List<RetentionActionResult> results;
    public RetentionActionSummary summary;

    public ApplyRetentionResponse(List<RetentionActionResult> results, RetentionActionSummary summary) {
        this.results = results;
        this.summary = summary;
    }

    /**
     * Individual retention action result
     */
    public static class RetentionActionResult {
        @JsonProperty("customer_id_hash")
        public String customer_id_hash;
        
        public String status; // "created" or "failed"
        
        @JsonProperty("case_ref_hash")
        public String case_ref_hash;
        
        @JsonProperty("error_code")
        public String error_code;
        
        @JsonProperty("error_message")
        public String error_message;

        public RetentionActionResult() {}

        public RetentionActionResult(String customer_id_hash, String status, String case_ref_hash, String error_code, String error_message) {
            this.customer_id_hash = customer_id_hash;
            this.status = status;
            this.case_ref_hash = case_ref_hash;
            this.error_code = error_code;
            this.error_message = error_message;
        }
    }

    /**
     * Summary of retention action processing
     */
    public static class RetentionActionSummary {
        public int total;
        public int created;
        public int failed;

        public RetentionActionSummary(int total, int created, int failed) {
            this.total = total;
            this.created = created;
            this.failed = failed;
        }
    }
}
