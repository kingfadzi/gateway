package com.example.gateway.risk.service;

import com.example.gateway.application.model.Application;
import com.example.gateway.application.repository.ApplicationRepository;
import com.example.gateway.risk.dto.*;
import com.example.gateway.risk.model.DomainRisk;
import com.example.gateway.risk.model.DomainRiskStatus;
import com.example.gateway.risk.model.RiskItem;
import com.example.gateway.risk.model.RiskPriority;
import com.example.gateway.risk.repository.DomainRiskRepository;
import com.example.gateway.risk.repository.RiskItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestration service for ARB Dashboard.
 * Handles scope-based filtering, application metadata enrichment, and aggregation calculations.
 *
 * Supports three scopes:
 * - my-queue: Apps where user has self-assigned risk items
 * - my-domain: Domain risks in ARB's domain
 * - all-domains: All domain risks
 */
@Service
public class ArbDashboardService {

    private static final Logger log = LoggerFactory.getLogger(ArbDashboardService.class);

    private static final List<DomainRiskStatus> ACTIVE_STATUSES = List.of(
        DomainRiskStatus.PENDING_ARB_REVIEW,
        DomainRiskStatus.UNDER_ARB_REVIEW,
        DomainRiskStatus.AWAITING_REMEDIATION,
        DomainRiskStatus.IN_PROGRESS
    );

    private final DomainRiskRepository domainRiskRepository;
    private final RiskItemRepository riskItemRepository;
    private final ApplicationRepository applicationRepository;
    private final HealthGradeCalculator healthGradeCalculator;

    public ArbDashboardService(
            DomainRiskRepository domainRiskRepository,
            RiskItemRepository riskItemRepository,
            ApplicationRepository applicationRepository,
            HealthGradeCalculator healthGradeCalculator) {
        this.domainRiskRepository = domainRiskRepository;
        this.riskItemRepository = riskItemRepository;
        this.applicationRepository = applicationRepository;
        this.healthGradeCalculator = healthGradeCalculator;
    }

    /**
     * Get applications with risk aggregations for ARB dashboard watchlist.
     *
     * @param arbName ARB identifier
     * @param scope Filtering scope: my-queue, my-domain, all-domains
     * @param userId User ID (required for my-queue)
     * @param includeRisks Include detailed risk items
     * @param page Page number
     * @param pageSize Page size
     * @return Application watchlist response
     */
    public ApplicationWatchlistResponse getApplicationsForArb(
            String arbName,
            String scope,
            String userId,
            boolean includeRisks,
            int page,
            int pageSize) {

        log.info("Getting applications for ARB: {}, scope: {}, userId: {}, page: {}, size: {}",
                arbName, scope, userId, page, pageSize);

        // Validate scope and userId
        validateScope(scope, userId);

        // Get unique app IDs based on scope
        List<String> appIds = getAppIdsByScope(scope, arbName, userId);
        log.debug("Found {} unique app IDs for scope: {}", appIds.size(), scope);

        if (appIds.isEmpty()) {
            return new ApplicationWatchlistResponse(
                scope, arbName, userId, 0, page, pageSize, List.of());
        }

        // Batch fetch applications (avoid N+1)
        Map<String, Application> applicationMap = fetchApplicationsBatch(appIds);

        // Batch fetch domain risks for these apps
        List<DomainRisk> domainRisks = domainRiskRepository.findByAppIdsAndStatuses(appIds, ACTIVE_STATUSES);

        // Group domain risks by app ID
        Map<String, List<DomainRisk>> domainRisksByApp = domainRisks.stream()
                .collect(Collectors.groupingBy(DomainRisk::getAppId));

        // Batch fetch "assigned to me" breakdowns (single query, avoids N+1)
        Map<String, RiskBreakdown> assignedToMeBreakdowns =
            fetchAssignedToMeBreakdownsBatch(appIds, userId);

        log.debug("Loaded assigned-to-me breakdowns for {} apps", assignedToMeBreakdowns.size());

        // Build ApplicationWithRisks for each app
        List<ApplicationWithRisks> applications = appIds.stream()
                .map(appId -> buildApplicationWithRisks(
                        appId,
                        applicationMap.get(appId),
                        domainRisksByApp.getOrDefault(appId, List.of()),
                        assignedToMeBreakdowns.getOrDefault(appId, RiskBreakdown.empty()),
                        userId,
                        includeRisks))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(ApplicationWithRisks::aggregatedRiskScore).reversed()
                        .thenComparing(ApplicationWithRisks::totalOpenItems).reversed())
                .collect(Collectors.toList());

        // Apply pagination
        int start = page * pageSize;
        int end = Math.min(start + pageSize, applications.size());
        List<ApplicationWithRisks> paginatedApps = applications.subList(
                Math.min(start, applications.size()),
                Math.min(end, applications.size()));

        log.info("Returning {} applications (page {}, total: {})", paginatedApps.size(), page, applications.size());

        return new ApplicationWatchlistResponse(
                scope,
                arbName,
                userId,
                applications.size(),
                page,
                pageSize,
                paginatedApps
        );
    }

    /**
     * Get dashboard metrics for ARB HUD.
     *
     * @param arbName ARB identifier
     * @param scope Filtering scope
     * @param userId User ID (required for my-queue)
     * @return Dashboard metrics response
     */
    public DashboardMetricsResponse getMetricsForArb(
            String arbName,
            String scope,
            String userId) {

        log.info("Getting metrics for ARB: {}, scope: {}, userId: {}", arbName, scope, userId);

        // Validate scope and userId
        validateScope(scope, userId);

        // Get app IDs for scope
        List<String> appIds = getAppIdsByScope(scope, arbName, userId);

        // Calculate metrics - use user-specific queries for "my-queue", app-based for others
        int criticalCount;
        int openItemsCount;
        double averageRiskScore;
        RecentActivityMetrics recentActivity;

        if ("my-queue".equals(scope)) {
            // User-specific metrics: count only risks assigned to this user
            criticalCount = calculateCriticalCountByUser(userId);
            openItemsCount = calculateOpenItemsCountByUser(userId);
            averageRiskScore = calculateAverageRiskScoreByUser(userId);
            recentActivity = calculateRecentActivityByUser(userId);
        } else {
            // App-based metrics: count all risks in the scoped apps
            criticalCount = calculateCriticalCount(appIds);
            openItemsCount = calculateOpenItemsCount(appIds);
            averageRiskScore = calculateAverageRiskScore(appIds);
            recentActivity = calculateRecentActivity(appIds);
        }

        // Awaiting triage: count OPEN risk items (not domain risks)
        int pendingReviewCount = "my-queue".equals(scope)
            ? calculateAwaitingTriageCountByUser(userId)
            : calculateAwaitingTriageCount(appIds);
        String healthGrade = healthGradeCalculator.calculateHealthGrade(averageRiskScore);

        log.info("Metrics calculated: critical={}, open={}, pending={}, avgScore={}, grade={}",
                criticalCount, openItemsCount, pendingReviewCount, averageRiskScore, healthGrade);

        return new DashboardMetricsResponse(
                scope,
                arbName,
                userId,
                criticalCount,
                openItemsCount,
                pendingReviewCount,
                averageRiskScore,
                healthGrade,
                recentActivity
        );
    }

    /**
     * Get app-centric dashboard metrics for ARB HUD.
     * Focuses on counting applications at risk rather than individual risk items.
     *
     * @param arbName ARB identifier
     * @param scope Filtering scope: my-queue, my-domain, all-domains
     * @param userId User ID (required for my-queue)
     * @return App-centric metrics response
     */
    public ApplicationCentricMetricsResponse getAppCentricMetricsForArb(
            String arbName,
            String scope,
            String userId) {

        log.info("Getting app-centric metrics for ARB: {}, scope: {}, userId: {}", arbName, scope, userId);

        // Validate scope and userId
        validateScope(scope, userId);

        // Get app IDs for scope
        List<String> appIds = getAppIdsByScope(scope, arbName, userId);

        // Calculate app-centric metrics - use different queries based on scope
        int applicationsAtRisk;
        int criticalApplications;
        int highRiskApplications;
        int applicationsAwaitingTriage;
        int totalOpenItems;
        ApplicationActivityMetrics recentActivity;

        if ("my-queue".equals(scope)) {
            // User-specific app counts: apps where user has assigned risks
            applicationsAtRisk = appIds.size();  // Already filtered by user's assigned risks
            criticalApplications = calculateCriticalApplicationsByUser(userId);
            highRiskApplications = calculateHighRiskApplicationsByUser(appIds);
            applicationsAwaitingTriage = calculateApplicationsAwaitingTriageByUser(userId);
            totalOpenItems = calculateOpenItemsCountByUser(userId);
            recentActivity = calculateAppActivityByUser(userId);
        } else {
            // ARB-wide or domain-specific app counts
            applicationsAtRisk = calculateApplicationsAtRisk(arbName, appIds, scope);
            criticalApplications = calculateCriticalApplications(appIds);
            highRiskApplications = calculateHighRiskApplications(appIds);
            applicationsAwaitingTriage = calculateApplicationsAwaitingTriage(appIds);
            totalOpenItems = calculateOpenItemsCount(appIds);
            recentActivity = calculateAppActivity(appIds);
        }

        // Calculate average items per app (concentration indicator)
        double averageItemsPerApp = applicationsAtRisk > 0
                ? (double) totalOpenItems / applicationsAtRisk
                : 0.0;

        // Get risk level distribution (uses domain_risk table for fast aggregation)
        Map<String, Integer> applicationsByRiskLevel = "my-queue".equals(scope)
                ? getApplicationRiskLevelDistributionByAppIds(appIds)
                : getApplicationRiskLevelDistribution(arbName, appIds, scope);

        // Calculate health grade based on % critical applications
        String healthGrade = ApplicationCentricMetricsResponse.calculateHealthGrade(
                criticalApplications, applicationsAtRisk);

        log.info("App-centric metrics calculated: appsAtRisk={}, critical={}, highRisk={}, avgItemsPerApp={}, grade={}",
                applicationsAtRisk, criticalApplications, highRiskApplications, averageItemsPerApp, healthGrade);

        return new ApplicationCentricMetricsResponse(
                scope,
                arbName,
                userId,
                applicationsAtRisk,
                criticalApplications,
                highRiskApplications,
                applicationsAwaitingTriage,
                totalOpenItems,
                averageItemsPerApp,
                applicationsByRiskLevel,
                recentActivity,
                healthGrade
        );
    }

    /**
     * Assign application to ARB member.
     * Updates assigned_to and assigned_to_name for all domain risks belonging to the app+ARB.
     *
     * @param arbName ARB identifier (e.g., security, operations)
     * @param appId Application ID
     * @param assignedTo User ID
     * @param assignedToName Display name (optional)
     * @return Assignment response
     */
    public AssignDomainRiskResponse assignApplicationToArbMember(
            String arbName,
            String appId,
            String assignedTo,
            String assignedToName) {

        log.info("Assigning app {} to ARB member {} ({})", appId, assignedTo, assignedToName);

        // Verify application exists
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appId));

        // Get all domain risks for this app where assigned_arb matches arbName
        List<DomainRisk> domainRisks = domainRiskRepository.findByAppIdAndAssignedArb(appId, arbName);

        if (domainRisks.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("No domain risks found for app %s and ARB %s", appId, arbName));
        }

        // Update all domain risks
        OffsetDateTime assignedAt = OffsetDateTime.now();
        int updated = domainRiskRepository.updateAssignedToForAppAndArb(
                appId, arbName, assignedTo, assignedToName, assignedAt);

        log.info("Updated {} domain risks for app {} with assigned_to={}", updated, appId, assignedTo);

        return new AssignDomainRiskResponse(
                appId,
                app.getName(),
                assignedTo,
                assignedToName,
                assignedAt.toInstant(),
                String.format("Application successfully assigned to %s", assignedTo)
        );
    }

    // =====================
    // Helper Methods
    // =====================

    /**
     * Validate scope parameter and userId requirement.
     */
    private void validateScope(String scope, String userId) {
        if (!List.of("my-queue", "my-domain", "all-domains").contains(scope)) {
            throw new IllegalArgumentException(
                    "Invalid scope parameter. Must be one of: my-queue, my-domain, all-domains");
        }

        if ("my-queue".equals(scope) && (userId == null || userId.isBlank())) {
            throw new IllegalArgumentException("userId is required when scope=my-queue");
        }
    }

    /**
     * Get app IDs based on scope filtering.
     * - my-queue: Apps where user has self-assigned risk items (risk_item.assigned_to)
     * - my-domain: Apps with domain risks in ARB's domain
     * - all-domains: All apps with active domain risks
     */
    private List<String> getAppIdsByScope(String scope, String arbName, String userId) {
        return switch (scope) {
            case "my-queue" -> riskItemRepository.findAppsWithAssignedRisks(userId);
            case "my-domain" -> domainRiskRepository.findAppIdsByDomain(arbName, ACTIVE_STATUSES);
            case "all-domains" -> domainRiskRepository.findAllAppIdsByStatuses(ACTIVE_STATUSES);
            default -> throw new IllegalArgumentException("Invalid scope: " + scope);
        };
    }

    /**
     * Batch fetch applications by IDs to avoid N+1 queries.
     * Follows ProfileServiceImpl pattern.
     */
    private Map<String, Application> fetchApplicationsBatch(List<String> appIds) {
        List<Application> applications = applicationRepository.findAllById(appIds);
        return applications.stream()
                .collect(Collectors.toMap(Application::getAppId, app -> app));
    }

    /**
     * Build ApplicationWithRisks from application and domain risks.
     */
    private ApplicationWithRisks buildApplicationWithRisks(
            String appId,
            Application app,
            List<DomainRisk> domainRisks,
            RiskBreakdown assignedToMeBreakdown,
            String userId,
            boolean includeRisks) {

        if (app == null) {
            log.warn("Application not found for appId: {}, skipping", appId);
            return null;
        }

        // Calculate aggregations
        Integer aggregatedRiskScore = domainRisks.stream()
                .map(DomainRisk::getPriorityScore)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        Integer totalOpenItems = domainRisks.stream()
                .map(DomainRisk::getOpenItems)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        RiskBreakdown riskBreakdown = calculateRiskBreakdown(appId);

        List<String> domains = domainRisks.stream()
                .map(DomainRisk::getRiskRatingDimension)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Boolean hasAssignedRisks = domainRisks.stream()
                .anyMatch(dr -> userId != null && userId.equals(dr.getAssignedTo()));

        OffsetDateTime lastActivityDate = getLastActivityForApp(appId, domainRisks);

        // Build domain risk summaries
        List<DomainRiskSummaryDto> domainRiskSummaries = domainRisks.stream()
                .map(this::toDomainRiskSummaryDto)
                .sorted(Comparator.comparing(DomainRiskSummaryDto::priorityScore).reversed())
                .collect(Collectors.toList());

        // Get detailed risk items if requested
        List<RiskItemResponse> risks = includeRisks
                ? riskItemRepository.findByAppId(appId).stream()
                    .map(RiskDtoMapper::toRiskItemResponse)
                    .collect(Collectors.toList())
                : List.of();

        return new ApplicationWithRisks(
                app.getAppId(),
                app.getAppId(),
                app.getName(),
                app.getAppCriticalityAssessment(),
                app.getTransactionCycle(),
                app.getProductOwner(),
                app.getProductOwnerBrid(),
                aggregatedRiskScore,
                totalOpenItems,
                riskBreakdown,
                domains,
                hasAssignedRisks,
                lastActivityDate,
                domainRiskSummaries,
                risks,
                assignedToMeBreakdown
        );
    }

    /**
     * Calculate risk breakdown by priority for an application.
     */
    private RiskBreakdown calculateRiskBreakdown(String appId) {
        List<Object[]> breakdownData = riskItemRepository.getRiskBreakdownByApp(appId);

        Map<RiskPriority, Integer> countsByPriority = breakdownData.stream()
                .collect(Collectors.toMap(
                        row -> (RiskPriority) row[0],
                        row -> ((Long) row[1]).intValue()
                ));

        return new RiskBreakdown(
                countsByPriority.getOrDefault(RiskPriority.CRITICAL, 0),
                countsByPriority.getOrDefault(RiskPriority.HIGH, 0),
                countsByPriority.getOrDefault(RiskPriority.MEDIUM, 0),
                countsByPriority.getOrDefault(RiskPriority.LOW, 0)
        );
    }

    /**
     * Batch load "assigned to me" breakdowns for multiple apps.
     * Returns: Map<appId, RiskBreakdown>
     * Only loads if userId is provided.
     * Uses single batch query to avoid N+1 problem.
     */
    private Map<String, RiskBreakdown> fetchAssignedToMeBreakdownsBatch(
            List<String> appIds,
            String userId) {

        if (userId == null || userId.isBlank() || appIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> breakdownData = riskItemRepository.getAssignedToMeBreakdownByApps(appIds, userId);

        // Group by appId first, then by priority
        Map<String, Map<RiskPriority, Integer>> groupedByApp = new HashMap<>();

        for (Object[] row : breakdownData) {
            String appId = (String) row[0];
            RiskPriority priority = (RiskPriority) row[1];
            Integer count = ((Long) row[2]).intValue();

            groupedByApp
                .computeIfAbsent(appId, k -> new HashMap<>())
                .put(priority, count);
        }

        // Convert to RiskBreakdown per app
        Map<String, RiskBreakdown> result = new HashMap<>();
        for (Map.Entry<String, Map<RiskPriority, Integer>> entry : groupedByApp.entrySet()) {
            Map<RiskPriority, Integer> counts = entry.getValue();
            result.put(
                entry.getKey(),
                new RiskBreakdown(
                    counts.getOrDefault(RiskPriority.CRITICAL, 0),
                    counts.getOrDefault(RiskPriority.HIGH, 0),
                    counts.getOrDefault(RiskPriority.MEDIUM, 0),
                    counts.getOrDefault(RiskPriority.LOW, 0)
                )
            );
        }

        return result;
    }

    /**
     * Get last activity date for application.
     */
    private OffsetDateTime getLastActivityForApp(String appId, List<DomainRisk> domainRisks) {
        OffsetDateTime domainRiskActivity = domainRisks.stream()
                .map(DomainRisk::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        OffsetDateTime riskItemActivity = riskItemRepository.getLastActivityForApp(appId);

        if (domainRiskActivity == null) return riskItemActivity;
        if (riskItemActivity == null) return domainRiskActivity;

        return domainRiskActivity.isAfter(riskItemActivity)
                ? domainRiskActivity
                : riskItemActivity;
    }

    /**
     * Convert DomainRisk to DomainRiskSummaryDto.
     */
    private DomainRiskSummaryDto toDomainRiskSummaryDto(DomainRisk dr) {
        return new DomainRiskSummaryDto(
                dr.getDomainRiskId(),
                dr.getRiskRatingDimension(),
                dr.getStatus(),
                dr.getPriorityScore(),
                dr.getOpenItems(),
                dr.getAssignedArb(),
                dr.getAssignedTo(),
                dr.getAssignedToName(),
                dr.getAssignedAt()
        );
    }

    /**
     * Calculate critical count for metrics.
     * Uses scoped queries to filter by app IDs based on view (my-queue, my-domain, all-domains).
     */
    private int calculateCriticalCount(List<String> appIds) {
        if (appIds.isEmpty()) return 0;
        return (int) riskItemRepository.countCriticalItemsByAppIds(appIds);
    }

    /**
     * Calculate open items count for metrics.
     * Uses scoped queries to filter by app IDs based on view (my-queue, my-domain, all-domains).
     */
    private int calculateOpenItemsCount(List<String> appIds) {
        if (appIds.isEmpty()) return 0;
        return (int) riskItemRepository.countTotalOpenItemsByAppIds(appIds);
    }

    /**
     * Calculate awaiting triage count for metrics.
     * Counts OPEN risk items (needs attention, not yet being worked on).
     * Uses scoped queries to filter by app IDs based on view (my-domain, all-domains).
     */
    private int calculateAwaitingTriageCount(List<String> appIds) {
        if (appIds.isEmpty()) return 0;
        return (int) riskItemRepository.countAwaitingTriageByAppIds(appIds);
    }

    /**
     * Calculate average risk score for metrics.
     * Uses scoped queries to filter by app IDs based on view (my-queue, my-domain, all-domains).
     */
    private double calculateAverageRiskScore(List<String> appIds) {
        if (appIds.isEmpty()) return 0.0;
        Double avgScore = riskItemRepository.getAveragePriorityScoreByAppIds(appIds);
        return avgScore != null ? avgScore : 0.0;
    }

    /**
     * Calculate recent activity metrics (7-day and 30-day windows).
     * Uses scoped queries to filter by app IDs based on view (my-queue, my-domain, all-domains).
     */
    private RecentActivityMetrics calculateRecentActivity(List<String> appIds) {
        if (appIds.isEmpty()) return RecentActivityMetrics.empty();

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);

        int newRisks7d = (int) riskItemRepository.countCreatedAfterByAppIds(appIds, sevenDaysAgo);
        int resolved7d = (int) riskItemRepository.countResolvedAfterByAppIds(appIds, sevenDaysAgo);
        int newRisks30d = (int) riskItemRepository.countCreatedAfterByAppIds(appIds, thirtyDaysAgo);
        int resolved30d = (int) riskItemRepository.countResolvedAfterByAppIds(appIds, thirtyDaysAgo);

        return new RecentActivityMetrics(newRisks7d, resolved7d, newRisks30d, resolved30d);
    }

    // ============================================
    // User-Specific Metrics (for my-queue scope)
    // ============================================

    /**
     * Calculate critical count for a specific user.
     * Counts only risks assigned to this user (my-queue scope).
     */
    private int calculateCriticalCountByUser(String userId) {
        return (int) riskItemRepository.countCriticalItemsByUser(userId);
    }

    /**
     * Calculate open items count for a specific user.
     * Counts only risks assigned to this user (my-queue scope).
     */
    private int calculateOpenItemsCountByUser(String userId) {
        return (int) riskItemRepository.countOpenItemsByUser(userId);
    }

    /**
     * Calculate average risk score for a specific user.
     * Calculates average only for risks assigned to this user (my-queue scope).
     */
    private double calculateAverageRiskScoreByUser(String userId) {
        Double avgScore = riskItemRepository.getAveragePriorityScoreByUser(userId);
        return avgScore != null ? avgScore : 0.0;
    }

    /**
     * Calculate recent activity metrics for a specific user (7-day and 30-day windows).
     * Counts only risks assigned to this user (my-queue scope).
     */
    private RecentActivityMetrics calculateRecentActivityByUser(String userId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);

        int newRisks7d = (int) riskItemRepository.countCreatedAfterByUser(userId, sevenDaysAgo);
        int resolved7d = (int) riskItemRepository.countResolvedAfterByUser(userId, sevenDaysAgo);
        int newRisks30d = (int) riskItemRepository.countCreatedAfterByUser(userId, thirtyDaysAgo);
        int resolved30d = (int) riskItemRepository.countResolvedAfterByUser(userId, thirtyDaysAgo);

        return new RecentActivityMetrics(newRisks7d, resolved7d, newRisks30d, resolved30d);
    }

    /**
     * Calculate awaiting triage count for a specific user.
     * Counts OPEN risk items assigned to this user (needs attention, not yet being worked on).
     * User-specific version for "my-queue" scope.
     */
    private int calculateAwaitingTriageCountByUser(String userId) {
        return (int) riskItemRepository.countAwaitingTriageByUser(userId);
    }

    // ============================================
    // APP-CENTRIC METRICS HELPERS
    // ============================================

    /**
     * Count total applications at risk.
     * Uses domain_risk table for fast aggregation.
     */
    private int calculateApplicationsAtRisk(String arbName, List<String> appIds, String scope) {
        if (appIds.isEmpty()) return 0;

        // For my-domain: use ARB-filtered query (faster)
        // For all-domains: use app IDs list (already filtered by active statuses)
        if ("my-domain".equals(scope)) {
            return (int) domainRiskRepository.countApplicationsAtRisk(arbName, ACTIVE_STATUSES);
        } else {
            return (int) domainRiskRepository.countApplicationsAtRiskByAppIds(appIds, ACTIVE_STATUSES);
        }
    }

    /**
     * Count applications with ≥1 CRITICAL priority risk item.
     * Uses risk_item table for granular filtering.
     */
    private int calculateCriticalApplications(List<String> appIds) {
        if (appIds.isEmpty()) return 0;
        return (int) riskItemRepository.countApplicationsWithCriticalItems(appIds);
    }

    /**
     * Count applications with ≥1 CRITICAL priority risk item (user-scoped).
     */
    private int calculateCriticalApplicationsByUser(String userId) {
        return (int) riskItemRepository.countApplicationsWithCriticalItemsByUser(userId);
    }

    /**
     * Count applications with high risk scores (≥70).
     * Uses domain_risk table for pre-calculated priority scores.
     */
    private int calculateHighRiskApplications(List<String> appIds) {
        if (appIds.isEmpty()) return 0;
        return (int) domainRiskRepository.countHighRiskApplicationsByAppIds(appIds, ACTIVE_STATUSES, 70.0);
    }

    /**
     * Count applications with high risk scores (≥70) for user's assigned apps.
     */
    private int calculateHighRiskApplicationsByUser(List<String> appIds) {
        if (appIds.isEmpty()) return 0;
        return (int) domainRiskRepository.countHighRiskApplicationsByAppIds(appIds, ACTIVE_STATUSES, 70.0);
    }

    /**
     * Count applications with ≥1 OPEN status risk item (awaiting triage).
     * Uses risk_item table for status-level filtering.
     */
    private int calculateApplicationsAwaitingTriage(List<String> appIds) {
        if (appIds.isEmpty()) return 0;
        return (int) riskItemRepository.countApplicationsAwaitingTriage(appIds);
    }

    /**
     * Count applications with ≥1 OPEN status risk item (user-scoped).
     */
    private int calculateApplicationsAwaitingTriageByUser(String userId) {
        return (int) riskItemRepository.countApplicationsAwaitingTriageByUser(userId);
    }

    /**
     * Calculate application-level activity metrics (7-day and 30-day windows).
     * Counts unique apps with new risks or resolutions.
     */
    private ApplicationActivityMetrics calculateAppActivity(List<String> appIds) {
        if (appIds.isEmpty()) return new ApplicationActivityMetrics(0, 0, 0, 0);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);

        int appsWithNewRisks7d = (int) riskItemRepository.countApplicationsWithNewRisks(appIds, sevenDaysAgo);
        int appsWithResolutions7d = (int) riskItemRepository.countApplicationsWithResolutions(appIds, sevenDaysAgo);
        int appsWithNewRisks30d = (int) riskItemRepository.countApplicationsWithNewRisks(appIds, thirtyDaysAgo);
        int appsWithResolutions30d = (int) riskItemRepository.countApplicationsWithResolutions(appIds, thirtyDaysAgo);

        return new ApplicationActivityMetrics(
                appsWithNewRisks7d,
                appsWithResolutions7d,
                appsWithNewRisks30d,
                appsWithResolutions30d
        );
    }

    /**
     * Calculate application-level activity metrics (user-scoped).
     */
    private ApplicationActivityMetrics calculateAppActivityByUser(String userId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        OffsetDateTime thirtyDaysAgo = now.minusDays(30);

        int appsWithNewRisks7d = (int) riskItemRepository.countApplicationsWithNewRisksByUser(userId, sevenDaysAgo);
        int appsWithResolutions7d = (int) riskItemRepository.countApplicationsWithResolutionsByUser(userId, sevenDaysAgo);
        int appsWithNewRisks30d = (int) riskItemRepository.countApplicationsWithNewRisksByUser(userId, thirtyDaysAgo);
        int appsWithResolutions30d = (int) riskItemRepository.countApplicationsWithResolutionsByUser(userId, thirtyDaysAgo);

        return new ApplicationActivityMetrics(
                appsWithNewRisks7d,
                appsWithResolutions7d,
                appsWithNewRisks30d,
                appsWithResolutions30d
        );
    }

    /**
     * Get application risk level distribution (CRITICAL/HIGH/MEDIUM/LOW).
     * Uses domain_risk table for pre-calculated priority scores.
     */
    private Map<String, Integer> getApplicationRiskLevelDistribution(String arbName, List<String> appIds, String scope) {
        // Convert enum statuses to strings for native query
        List<String> statusStrings = ACTIVE_STATUSES.stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        List<Object[]> distribution;

        if ("my-domain".equals(scope)) {
            distribution = domainRiskRepository.getApplicationRiskLevelDistribution(arbName, statusStrings);
        } else {
            distribution = domainRiskRepository.getApplicationRiskLevelDistributionByAppIds(appIds, statusStrings);
        }

        return distribution.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
    }

    /**
     * Get application risk level distribution for user's assigned apps.
     */
    private Map<String, Integer> getApplicationRiskLevelDistributionByAppIds(List<String> appIds) {
        if (appIds.isEmpty()) return Map.of();

        // Convert enum statuses to strings for native query
        List<String> statusStrings = ACTIVE_STATUSES.stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        List<Object[]> distribution = domainRiskRepository.getApplicationRiskLevelDistributionByAppIds(
                appIds, statusStrings);

        return distribution.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
    }
}
