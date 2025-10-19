package usecases.telcoRevenueRetention.core.records;

import java.util.List;

/**
 * Represents a customer record with payment failures and complaints
 */
public record Customer(String id, String region, double revenue, List<Payment> fails, int openComplaints) {
}
