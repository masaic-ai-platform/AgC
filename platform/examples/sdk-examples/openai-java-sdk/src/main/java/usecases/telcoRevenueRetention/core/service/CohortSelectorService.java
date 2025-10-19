package usecases.telcoRevenueRetention.core.service;

import usecases.telcoRevenueRetention.core.records.Customer;
import usecases.telcoRevenueRetention.core.repository.CustomerDataRepository;
import usecases.telcoRevenueRetention.data.RetentionCohortRequest;
import usecases.telcoRevenueRetention.data.RetentionCohortResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for selecting retention cohorts based on customer data and criteria
 */
public class CohortSelectorService {
    private static final String SALT = "enterprise-salt-rotate-me";
    private final CustomerDataRepository customerRepository;

    public CohortSelectorService() {
        this.customerRepository = new CustomerDataRepository();
    }

    public List<RetentionCohortResponse.CohortItem> select(RetentionCohortRequest p) {
        long cutoff = Instant.now().minus(Duration.ofDays(p.getLookbackDays())).toEpochMilli();

        return customerRepository.getAllCustomers().stream().filter(c -> c.revenue() >= p.getMinRevenue())
                .filter(c -> p.getRegion() == null || p.getRegion().equalsIgnoreCase(c.region()))
                .map(c -> new Object[]
                        {c, (int) c.fails().stream().filter(f -> f.tsMs() > cutoff).count()})
                .filter(a -> (int) a[1] >= p.getMinFailures() && ((Customer) a[0]).openComplaints() >= p.getMinComplaints())
                .map(a -> {
                    Customer c = (Customer) a[0];
                    int fails = (int) a[1];
                    double score = risk(fails, c.openComplaints());
                    return new Object[]{c, score};
                })
                .sorted(Comparator.<Object[]>comparingDouble(x -> -(double) x[1]) // risk desc
                        .thenComparingDouble(x -> -((Customer) x[0]).revenue()) // revenue desc
                        .thenComparing(x -> ((Customer) x[0]).id())) // id asc
                .limit(p.getLimit()).map(a -> new RetentionCohortResponse.CohortItem(hashShort(((Customer) a[0]).id()), (double) a[1])).collect(Collectors.toList());
    }

    private double risk(int failures, int complaints) {
        double f = Math.min(failures / 3.0, 1.0), c = Math.min(complaints / 2.0, 1.0);
        return Math.max(0, Math.min(1, 0.6 * f + 0.4 * c));
    }

    private String hashShort(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SALT.getBytes(StandardCharsets.UTF_8));
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return String.format("%02x%02x%02x%02x", d[0], d[1], d[2], d[3]);
        } catch (Exception e) {
            return Integer.toHexString(Objects.hashCode(s));
        }
    }
}
