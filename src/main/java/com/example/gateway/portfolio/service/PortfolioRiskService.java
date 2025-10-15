package com.example.gateway.portfolio.service;

import com.example.gateway.portfolio.dto.CriticalApp;
import com.example.gateway.portfolio.dto.PortfolioRiskSummary;
import com.example.gateway.portfolio.repository.PortfolioRiskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for portfolio-level risk aggregations and dashboards.
 * Provides high-level risk metrics for Product Owners.
 */
@Service
public class PortfolioRiskService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRiskService.class);

    private final PortfolioRiskRepository repository;

    public PortfolioRiskService(PortfolioRiskRepository repository) {
        this.repository = repository;
    }

    /**
     * Get comprehensive risk summary for a product owner's portfolio.
     *
     * @param productOwner Product owner email or ID
     * @return Portfolio risk summary with actionable metrics
     */
    public PortfolioRiskSummary getPortfolioRiskSummary(String productOwner) {
        log.info("Getting portfolio risk summary for product owner: {}", productOwner);

        long startTime = System.currentTimeMillis();

        // Execute all queries in parallel could be optimized, but keeping simple for now
        int actionRequired = repository.countActionRequired(productOwner);
        int blockingCompliance = repository.countBlockingCompliance(productOwner);
        int missingEvidence = repository.countMissingEvidence(productOwner);
        int pendingReview = repository.countPendingReview(productOwner);
        int escalated = repository.countEscalated(productOwner);
        int recentWins = repository.countRecentWins(productOwner);
        List<CriticalApp> criticalApps = repository.getCriticalApps(productOwner);
        int totalApps = repository.countTotalApps(productOwner);
        int appsWithRisks = repository.countAppsWithRisks(productOwner);

        PortfolioRiskSummary summary = new PortfolioRiskSummary(
            actionRequired,
            blockingCompliance,
            missingEvidence,
            pendingReview,
            escalated,
            recentWins,
            criticalApps,
            totalApps,
            appsWithRisks
        );

        long duration = System.currentTimeMillis() - startTime;
        log.info("Portfolio risk summary completed in {}ms - actionRequired: {}, blockingCompliance: {}, totalApps: {}",
                duration, actionRequired, blockingCompliance, totalApps);

        return summary;
    }
}
