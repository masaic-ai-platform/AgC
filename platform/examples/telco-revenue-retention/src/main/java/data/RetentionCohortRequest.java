package data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request parameters for SelectRetentionCohort tool
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetentionCohortRequest {
    
    @JsonProperty("min_revenue")
    private double minRevenue = 100_000.0;
    
    @JsonProperty("lookback_days")
    private int lookbackDays = 60;
    
    @JsonProperty("min_payment_failures")
    private int minFailures = 2;
    
    @JsonProperty("min_open_complaints")
    private int minComplaints = 1;
    
    private int limit = 500;
    
    private String region = null;

    // Default constructor for Jackson
    public RetentionCohortRequest() {}

    // Getters and setters for Jackson
    public double getMinRevenue() {
        return minRevenue;
    }

    public void setMinRevenue(double minRevenue) {
        this.minRevenue = minRevenue;
    }

    public int getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(int lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public int getMinFailures() {
        return minFailures;
    }

    public void setMinFailures(int minFailures) {
        this.minFailures = minFailures;
    }

    public int getMinComplaints() {
        return minComplaints;
    }

    public void setMinComplaints(int minComplaints) {
        this.minComplaints = minComplaints;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = (region == null || region.isBlank()) ? null : region.trim();
    }
}
