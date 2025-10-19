package usecases.telcoRevenueRetention.core.repository;

import usecases.telcoRevenueRetention.core.records.Customer;
import usecases.telcoRevenueRetention.core.records.Payment;

import java.util.List;

/**
 * Repository for customer data operations
 * Provides access to customer data for retention analysis
 */
public class CustomerDataRepository {

    private static Payment createPaymentDaysAgo(int days) {
        return new Payment(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000L);
    }

    private static final List<Customer> CUSTOMER_DATA = List.of(
            // Mock records
            new Customer("CUST-001", "IN", 250000, List.of(createPaymentDaysAgo(10), createPaymentDaysAgo(25), createPaymentDaysAgo(50), createPaymentDaysAgo(120)), 2),
            new Customer("CUST-002", "US", 180000, List.of(createPaymentDaysAgo(15), createPaymentDaysAgo(59)), 1),
            new Customer("CUST-003", "US", 300000, List.of(createPaymentDaysAgo(5), createPaymentDaysAgo(35)), 1),
            new Customer("CUST-004", "IN", 220000, List.of(createPaymentDaysAgo(90), createPaymentDaysAgo(120)), 3),
            new Customer("CUST-005", "EU", 50000, List.of(createPaymentDaysAgo(10), createPaymentDaysAgo(15)), 2),
            new Customer("CUST-006", "EU", 210000, List.of(createPaymentDaysAgo(7), createPaymentDaysAgo(20)), 0),
            new Customer("CUST-007", "IN", 100000, List.of(createPaymentDaysAgo(60), createPaymentDaysAgo(1)), 1),
            new Customer("CUST-008", "IN", 150000, List.of(createPaymentDaysAgo(5), createPaymentDaysAgo(8), createPaymentDaysAgo(9)), 2), // Matches all criteria
            new Customer("CUST-009", "IN", 120000, List.of(createPaymentDaysAgo(3), createPaymentDaysAgo(7)), 2), // Matches all criteria
            new Customer("CUST-010", "IN", 200000, List.of(createPaymentDaysAgo(1), createPaymentDaysAgo(4), createPaymentDaysAgo(6)), 3), // Matches all criteria
            new Customer("CUST-011", "IN", 110000, List.of(createPaymentDaysAgo(2), createPaymentDaysAgo(5), createPaymentDaysAgo(8), createPaymentDaysAgo(9)), 2), // Matches all criteria
            new Customer("CUST-012", "IN", 180000, List.of(createPaymentDaysAgo(6), createPaymentDaysAgo(7)), 1), // Revenue OK, region OK, failures OK, but complaints < 2
            new Customer("CUST-013", "IN", 130000, List.of(createPaymentDaysAgo(1), createPaymentDaysAgo(2)), 2), // Revenue OK, region OK, complaints OK, but failures < 2
            new Customer("CUST-014", "US", 150000, List.of(createPaymentDaysAgo(5), createPaymentDaysAgo(8)), 2), // Revenue OK, failures OK, complaints OK, but wrong region
            new Customer("CUST-015", "IN", 80000, List.of(createPaymentDaysAgo(3), createPaymentDaysAgo(6), createPaymentDaysAgo(9)), 2), // Region OK, failures OK, complaints OK, but revenue < 100000
            new Customer("CUST-016", "IN", 160000, List.of(createPaymentDaysAgo(15), createPaymentDaysAgo(20)), 2), // Revenue OK, region OK, complaints OK, but failures outside lookback period
            new Customer("CUST-017", "IN", 140000, List.of(createPaymentDaysAgo(1), createPaymentDaysAgo(3), createPaymentDaysAgo(5), createPaymentDaysAgo(7)), 3), // Matches all criteria
            new Customer("CUST-018", "IN", 190000, List.of(createPaymentDaysAgo(2), createPaymentDaysAgo(4), createPaymentDaysAgo(6), createPaymentDaysAgo(8), createPaymentDaysAgo(10)), 2) // Matches all criteria
    );

    /**
     * Retrieves all customer data
     *
     * @return List of all customers
     */
    public List<Customer> getAllCustomers() {
        //simulate with mock records
        return CUSTOMER_DATA;
    }
}
