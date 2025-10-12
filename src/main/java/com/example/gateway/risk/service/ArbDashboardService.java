package com.example.gateway.risk.service;

import com.example.gateway.risk.dto.ArbDashboardResponse;
import com.example.gateway.risk.model.DomainRisk;
import com.example.gateway.risk.model.DomainRiskStatus;
import com.example.gateway.risk.model.RiskItem;
import com.example.gateway.risk.repository.DomainRiskRepository;
import com.example.gateway.risk.repository.RiskItemRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building comprehensive ARB dashboard data.
 * Aggregates metrics from multiple sources for SME dashboard visualization.
 */
@Service
public class ArbDashboardService {

    private final DomainRiskRepository domainRiskRepository;
    private final RiskItemRepository riskItemRepository;

    public ArbDashboardService(
            DomainRiskRepository domainRiskRepository,
            RiskItemRepository riskItemRepository) {
        this.domainRiskRepository = domainRiskRepository;
        this.riskItemRepository = riskItemRepository;
    }

    /**
     * Build comprehensive dashboard data for an ARB.
     *
     * @param arbName ARB identifier (e.g., "security", "data")
     * @param statuses List of statuses to include (default: active statuses)
     * @return Complete dashboard metrics
     */
    public ArbDashboardResponse buildDashboard(String arbName, List<DomainRiskStatus> statuses) {
        // Get all domain risks for this ARB
        List<DomainRisk> domainRisks = domainRiskRepository.findByArbPrioritized(arbName, statuses);

        // Build overview metrics
        ArbDashboardResponse.OverviewMetrics overview = buildOverviewMetrics(domainRisks);

        // Build domain breakdown
        List<ArbDashboardResponse.DomainBreakdown> domains = buildDomainBreakdown(domainRisks);

        // Build application breakdown
        List<ArbDashboardResponse.AppBreakdown> topApps = buildAppBreakdown(domainRisks);

        // Build status distribution
        Map<String, Long> statusDist = buildStatusDistribution(domainRisks);

        // Build priority distribution
        ArbDashboardResponse.PriorityDistribution priorityDist = buildPriorityDistribution(domainRisks);

        // Build recent activity
        ArbDashboardResponse.RecentActivity recentActivity = buildRecentActivity(domainRisks);

        return new ArbDashboardResponse(
                arbName,
                overview,
                domains,
                topApps,
                statusDist,
                priorityDist,
                recentActivity
        );
    }

    private ArbDashboardResponse.OverviewMetrics buildOverviewMetrics(List<DomainRisk> domainRisks) {
        long totalDomainRisks = domainRisks.size();
        long totalOpenItems = domainRisks.stream()
                .mapToLong(dr -> dr.getOpenItems() != null ? dr.getOpenItems() : 0)
                .sum();

        long criticalCount = domainRisks.stream()
                .filter(dr -> dr.getPriorityScore() != null && dr.getPriorityScore() >= 90)
                .count();

        long highCount = domainRisks.stream()
                .filter(dr -> dr.getPriorityScore() != null && dr.getPriorityScore() >= 70)
                .count();

        long avgPriorityScore = (long) domainRisks.stream()
                .filter(dr -> dr.getPriorityScore() != null)
                .mapToInt(DomainRisk::getPriorityScore)
                .average()
                .orElse(0.0);

        long needsAttention = domainRisks.stream()
                .filter(dr -> dr.getPriorityScore() != null && dr.getPriorityScore() >= 70)
                .count();

        return new ArbDashboardResponse.OverviewMetrics(
                totalDomainRisks,
                totalOpenItems,
                criticalCount,
                highCount,
                avgPriorityScore,
                needsAttention
        );
    }

    private List<ArbDashboardResponse.DomainBreakdown> buildDomainBreakdown(List<DomainRisk> domainRisks) {
        // Group by domain
        Map<String, List<DomainRisk>> byDomain = domainRisks.stream()
                .collect(Collectors.groupingBy(DomainRisk::getDomain));

        return byDomain.entrySet().stream()
                .map(entry -> {
                    String domain = entry.getKey();
                    List<DomainRisk> risks = entry.getValue();

                    long riskCount = risks.size();
                    long openItems = risks.stream()
                            .mapToLong(dr -> dr.getOpenItems() != null ? dr.getOpenItems() : 0)
                            .sum();

                    long criticalItems = risks.stream()
                            .filter(dr -> dr.getPriorityScore() != null && dr.getPriorityScore() >= 90)
                            .mapToLong(dr -> dr.getOpenItems() != null ? dr.getOpenItems() : 0)
                            .sum();

                    double avgScore = risks.stream()
                            .filter(dr -> dr.getPriorityScore() != null)
                            .mapToInt(DomainRisk::getPriorityScore)
                            .average()
                            .orElse(0.0);

                    // Get status of highest priority risk
                    String topStatus = risks.stream()
                            .max(Comparator.comparing(DomainRisk::getPriorityScore, Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(dr -> dr.getStatus().name())
                            .orElse("UNKNOWN");

                    return new ArbDashboardResponse.DomainBreakdown(
                            domain,
                            riskCount,
                            openItems,
                            criticalItems,
                            avgScore,
                            topStatus
                    );
                })
                .sorted(Comparator.comparing(ArbDashboardResponse.DomainBreakdown::avgPriorityScore).reversed())
                .collect(Collectors.toList());
    }

    private List<ArbDashboardResponse.AppBreakdown> buildAppBreakdown(List<DomainRisk> domainRisks) {
        // Group by appId
        Map<String, List<DomainRisk>> byApp = domainRisks.stream()
                .collect(Collectors.groupingBy(DomainRisk::getAppId));

        return byApp.entrySet().stream()
                .map(entry -> {
                    String appId = entry.getKey();
                    List<DomainRisk> risks = entry.getValue();

                    long domainRiskCount = risks.size();
                    long totalOpenItems = risks.stream()
                            .mapToLong(dr -> dr.getOpenItems() != null ? dr.getOpenItems() : 0)
                            .sum();

                    int highestScore = risks.stream()
                            .filter(dr -> dr.getPriorityScore() != null)
                            .mapToInt(DomainRisk::getPriorityScore)
                            .max()
                            .orElse(0);

                    // Find domain with highest priority
                    String criticalDomain = risks.stream()
                            .max(Comparator.comparing(DomainRisk::getPriorityScore, Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(DomainRisk::getDomain)
                            .orElse("unknown");

                    return new ArbDashboardResponse.AppBreakdown(
                            appId,
                            null,  // App name would come from app service
                            domainRiskCount,
                            totalOpenItems,
                            highestScore,
                            criticalDomain
                    );
                })
                .sorted(Comparator.comparing(ArbDashboardResponse.AppBreakdown::highestPriorityScore).reversed())
                .limit(10)  // Top 10 apps
                .collect(Collectors.toList());
    }

    private Map<String, Long> buildStatusDistribution(List<DomainRisk> domainRisks) {
        return domainRisks.stream()
                .collect(Collectors.groupingBy(
                        dr -> dr.getStatus().name(),
                        Collectors.counting()
                ));
    }

    private ArbDashboardResponse.PriorityDistribution buildPriorityDistribution(List<DomainRisk> domainRisks) {
        long critical = domainRisks.stream()
                .filter(dr -> dr.getPriorityScore() != null && dr.getPriorityScore() >= 90)
                .count();

        long high = domainRisks.stream()
                .filter(dr -> dr.getPriorityScore() != null && dr.getPriorityScore() >= 70 && dr.getPriorityScore() < 90)
                .count();

        long medium = domainRisks.stream()
                .filter(dr -> dr.getPriorityScore() != null && dr.getPriorityScore() >= 40 && dr.getPriorityScore() < 70)
                .count();

        long low = domainRisks.stream()
                .filter(dr -> dr.getPriorityScore() != null && dr.getPriorityScore() < 40)
                .count();

        return new ArbDashboardResponse.PriorityDistribution(critical, high, medium, low);
    }

    private ArbDashboardResponse.RecentActivity buildRecentActivity(List<DomainRisk> domainRisks) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);

        long newLast7Days = domainRisks.stream()
                .filter(dr -> dr.getOpenedAt() != null && dr.getOpenedAt().isAfter(sevenDaysAgo))
                .count();

        long newLast30Days = domainRisks.stream()
                .filter(dr -> dr.getOpenedAt() != null && dr.getOpenedAt().isAfter(thirtyDaysAgo))
                .count();

        long resolvedLast7Days = domainRisks.stream()
                .filter(dr -> dr.getClosedAt() != null && dr.getClosedAt().isAfter(sevenDaysAgo))
                .count();

        long resolvedLast30Days = domainRisks.stream()
                .filter(dr -> dr.getClosedAt() != null && dr.getClosedAt().isAfter(thirtyDaysAgo))
                .count();

        return new ArbDashboardResponse.RecentActivity(
                newLast7Days,
                newLast30Days,
                resolvedLast7Days,
                resolvedLast30Days
        );
    }
}
