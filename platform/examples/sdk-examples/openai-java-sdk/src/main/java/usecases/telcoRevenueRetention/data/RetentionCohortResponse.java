package usecases.telcoRevenueRetention.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response for SelectRetentionCohort tool containing cohort items
 */
public class RetentionCohortResponse {
    
    public final List<CohortItem> customers;

    public RetentionCohortResponse(List<CohortItem> customers) {
        this.customers = customers;
    }

    /**
     * Individual cohort item containing customer hash and risk score
     */
    public static class CohortItem {
        @JsonProperty("id_hash")
        public final String id_hash;
        
        @JsonProperty("risk_score")
        public final double risk_score;

        public CohortItem(String id_hash, double risk_score) {
            this.id_hash = id_hash;
            this.risk_score = risk_score;
        }
    }
}
