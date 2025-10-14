package com.example.gateway.risk.repository;

import com.example.gateway.risk.model.RiskItem;
import com.example.gateway.risk.model.RiskItemStatus;
import com.example.gateway.risk.model.RiskPriority;
import com.example.gateway.risk.model.RiskCreationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for individual risk items.
 * Provides queries optimized for Product Owner (PO) workflows.
 */
public interface RiskItemRepository extends JpaRepository<RiskItem, String> {

    /**
     * Find all risk items for a domain risk
     */
    List<RiskItem> findByDomainRiskId(String domainRiskId);

    /**
     * Find all risk items for an application
     */
    List<RiskItem> findByAppId(String appId);

    /**
     * Find risk items for an app, ordered by priority (PO view).
     * Shows highest priority items first.
     */
    List<RiskItem> findByAppIdOrderByPriorityScoreDesc(String appId);

    /**
     * Find risk items for an app with specific status, prioritized.
     * Used for PO workbench filtered views.
     */
    @Query("""
        SELECT ri FROM RiskItem ri
        WHERE ri.appId = :appId
        AND ri.status = :status
        ORDER BY ri.priorityScore DESC, ri.openedAt ASC
        """)
    List<RiskItem> findByAppIdAndStatusPrioritized(
        @Param("appId") String appId,
        @Param("status") RiskItemStatus status);

    /**
     * Check if a risk item already exists for this evidence + field combination.
     * Used for deduplication in risk auto-creation.
     */
    boolean existsByAppIdAndFieldKeyAndTriggeringEvidenceId(
        String appId, String fieldKey, String triggeringEvidenceId);

    /**
     * Find risk items by field key (for field-specific analysis)
     */
    List<RiskItem> findByFieldKey(String fieldKey);

    /**
     * Find risk items triggered by specific evidence
     */
    List<RiskItem> findByTriggeringEvidenceId(String triggeringEvidenceId);

    /**
     * Find risk items by status
     */
    List<RiskItem> findByStatus(RiskItemStatus status);

    /**
     * Count risk items by domain risk and status
     */
    long countByDomainRiskIdAndStatus(String domainRiskId, RiskItemStatus status);

    /**
     * Count open risk items for a domain risk
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.domainRiskId = :domainRiskId
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countOpenItems(@Param("domainRiskId") String domainRiskId);

    /**
     * Count high priority risk items for a domain risk
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.domainRiskId = :domainRiskId
        AND ri.priority IN ('CRITICAL', 'HIGH')
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countHighPriorityItems(@Param("domainRiskId") String domainRiskId);

    /**
     * Get highest priority score for a domain risk (for aggregation)
     */
    @Query("""
        SELECT MAX(ri.priorityScore) FROM RiskItem ri
        WHERE ri.domainRiskId = :domainRiskId
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    Integer getMaxPriorityScore(@Param("domainRiskId") String domainRiskId);

    /**
     * Get risk breakdown by priority for an application.
     * Returns: [priority, count] for OPEN and IN_PROGRESS items only.
     * Used for ARB dashboard application watchlist.
     */
    @Query("""
        SELECT ri.priority, COUNT(ri)
        FROM RiskItem ri
        WHERE ri.appId = :appId
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        GROUP BY ri.priority
        """)
    List<Object[]> getRiskBreakdownByApp(@Param("appId") String appId);

    /**
     * Count risk items by app IDs and status.
     * Used for batch loading to avoid N+1 queries.
     */
    @Query("""
        SELECT ri.appId, COUNT(ri)
        FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        GROUP BY ri.appId
        """)
    List<Object[]> countOpenItemsByAppIds(@Param("appIds") List<String> appIds);

    /**
     * Count risk items created after a specific timestamp.
     * Used for recent activity metrics (7-day and 30-day windows).
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.openedAt >= :since
        """)
    long countCreatedAfter(@Param("since") java.time.OffsetDateTime since);

    /**
     * Count risk items resolved after a specific timestamp.
     * Used for recent activity metrics (7-day and 30-day windows).
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.resolvedAt >= :since
        AND ri.status IN ('RESOLVED', 'CLOSED')
        """)
    long countResolvedAfter(@Param("since") java.time.OffsetDateTime since);

    /**
     * Count CRITICAL priority risk items with OPEN or IN_PROGRESS status.
     * Used for dashboard metrics critical count.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.priority = 'CRITICAL'
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countCriticalItems();

    /**
     * Count risk items with OPEN status.
     * Used for dashboard metrics open items count.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.status = 'OPEN'
        """)
    long countOpenItems();

    /**
     * Calculate average priority score for OPEN and IN_PROGRESS items.
     * Used for dashboard metrics health grade calculation.
     */
    @Query("""
        SELECT AVG(ri.priorityScore) FROM RiskItem ri
        WHERE ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    Double getAveragePriorityScore();

    // ============================================
    // Scoped Metrics Queries (filtered by app IDs)
    // ============================================

    /**
     * Count CRITICAL priority risk items for specific apps.
     * Scoped version for dashboard metrics (my-queue, my-domain, all-domains).
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.priority = 'CRITICAL'
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countCriticalItemsByAppIds(@Param("appIds") List<String> appIds);

    /**
     * Count OPEN and IN_PROGRESS risk items across specific apps (total count, not grouped).
     * Scoped version for dashboard metrics (my-queue, my-domain, all-domains).
     * Renamed from countTotalOpenItemsByAppIds to reflect both statuses.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countTotalOpenItemsByAppIds(@Param("appIds") List<String> appIds);

    /**
     * Count OPEN risk items (awaiting triage) across specific apps.
     * OPEN = needs attention but not yet assigned/being worked on.
     * Used for "Awaiting Triage" metric on dashboard.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.status = 'OPEN'
        """)
    long countAwaitingTriageByAppIds(@Param("appIds") List<String> appIds);

    /**
     * Calculate average priority score for OPEN and IN_PROGRESS items in specific apps.
     * Scoped version for dashboard metrics (my-queue, my-domain, all-domains).
     */
    @Query("""
        SELECT AVG(ri.priorityScore) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    Double getAveragePriorityScoreByAppIds(@Param("appIds") List<String> appIds);

    /**
     * Count risk items created after timestamp for specific apps.
     * Scoped version for recent activity metrics (7-day and 30-day windows).
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.openedAt >= :since
        """)
    long countCreatedAfterByAppIds(@Param("appIds") List<String> appIds, @Param("since") java.time.OffsetDateTime since);

    /**
     * Count risk items resolved after timestamp for specific apps.
     * Scoped version for recent activity metrics (7-day and 30-day windows).
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.resolvedAt >= :since
        AND ri.status IN ('RESOLVED', 'CLOSED')
        """)
    long countResolvedAfterByAppIds(@Param("appIds") List<String> appIds, @Param("since") java.time.OffsetDateTime since);

    // ============================================
    // User-Specific Metrics (for my-queue scope)
    // ============================================

    /**
     * Count CRITICAL priority risk items assigned to a specific user.
     * User-specific version for "my-queue" scope.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.priority = 'CRITICAL'
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countCriticalItemsByUser(@Param("userId") String userId);

    /**
     * Count OPEN and IN_PROGRESS risk items assigned to a specific user.
     * User-specific version for "my-queue" scope.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countOpenItemsByUser(@Param("userId") String userId);

    /**
     * Calculate average priority score for OPEN and IN_PROGRESS items assigned to a specific user.
     * User-specific version for "my-queue" scope.
     */
    @Query("""
        SELECT AVG(ri.priorityScore) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    Double getAveragePriorityScoreByUser(@Param("userId") String userId);

    /**
     * Count risk items assigned to user and created after timestamp.
     * User-specific version for "my-queue" recent activity.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.openedAt >= :since
        """)
    long countCreatedAfterByUser(@Param("userId") String userId, @Param("since") java.time.OffsetDateTime since);

    /**
     * Count risk items assigned to user and resolved after timestamp.
     * User-specific version for "my-queue" recent activity.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.resolvedAt >= :since
        AND ri.status IN ('RESOLVED', 'CLOSED')
        """)
    long countResolvedAfterByUser(@Param("userId") String userId, @Param("since") java.time.OffsetDateTime since);

    /**
     * Count OPEN risk items (awaiting triage) assigned to a specific user.
     * OPEN = needs attention but not yet being actively worked on.
     * User-specific version for "my-queue" awaiting triage metric.
     */
    @Query("""
        SELECT COUNT(ri) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.status = 'OPEN'
        """)
    long countAwaitingTriageByUser(@Param("userId") String userId);

    /**
     * Get most recent activity timestamp for an application.
     * Checks both created_at and updated_at.
     */
    @Query("""
        SELECT MAX(GREATEST(ri.createdAt, ri.updatedAt))
        FROM RiskItem ri
        WHERE ri.appId = :appId
        """)
    java.time.OffsetDateTime getLastActivityForApp(@Param("appId") String appId);

    // ============================================
    // Assignment Queries
    // ============================================

    /**
     * Find all risk items assigned to a specific user.
     * Returns items ordered by priority (high to low).
     * Used for "My Assigned Risks" view.
     */
    @Query("""
        SELECT ri FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        ORDER BY ri.priorityScore DESC, ri.openedAt ASC
        """)
    List<RiskItem> findMyAssignedRisks(@Param("userId") String userId);

    /**
     * Find all unassigned risk items (no owner).
     * Returns items ordered by priority (high to low).
     * Used for "Available Risks" pool view.
     */
    @Query("""
        SELECT ri FROM RiskItem ri
        WHERE ri.assignedTo IS NULL
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        ORDER BY ri.priorityScore DESC, ri.openedAt ASC
        """)
    List<RiskItem> findUnassignedRisks();

    /**
     * Get unique apps for a user's assigned risks.
     * Used to populate "My Queue" app list.
     */
    @Query("""
        SELECT DISTINCT ri.appId
        FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    List<String> findAppsWithAssignedRisks(@Param("userId") String userId);

    /**
     * Get "assigned to me" risk breakdown by apps for a specific user.
     * Returns: [appId, priority, count] for OPEN and IN_PROGRESS items only.
     * Used for per-app "Assigned to Me" breakdown in watchlist.
     * Batch query to avoid N+1 problem.
     */
    @Query("""
        SELECT ri.appId, ri.priority, COUNT(ri)
        FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.assignedTo = :userId
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        GROUP BY ri.appId, ri.priority
        """)
    List<Object[]> getAssignedToMeBreakdownByApps(
        @Param("appIds") List<String> appIds,
        @Param("userId") String userId
    );

    /**
     * Find assigned risks for specific app (for drill-down from My Queue).
     */
    @Query("""
        SELECT ri FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.appId = :appId
        ORDER BY ri.priorityScore DESC
        """)
    List<RiskItem> findMyAssignedRisksByApp(
        @Param("userId") String userId,
        @Param("appId") String appId
    );

    /**
     * Count assigned risks by user (for workload metrics).
     * Returns [assignedTo, count] pairs.
     */
    @Query("""
        SELECT ri.assignedTo, COUNT(ri)
        FROM RiskItem ri
        WHERE ri.assignedTo IS NOT NULL
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        GROUP BY ri.assignedTo
        """)
    List<Object[]> countAssignedRisksByUser();

    /**
     * Find all risk items for an app, grouped by assignee.
     * Used to show "who's working on what" for an app.
     * Unassigned items appear first (NULLS FIRST).
     */
    @Query("""
        SELECT ri FROM RiskItem ri
        WHERE ri.appId = :appId
        ORDER BY ri.assignedTo NULLS FIRST, ri.priorityScore DESC
        """)
    List<RiskItem> findRisksByAppGroupedByAssignee(@Param("appId") String appId);

    // ============================================
    // Comprehensive Search Query
    // ============================================

    /**
     * Comprehensive search for risk items with multiple filters.
     * Supports filtering by: appId, assignedTo, status, priority, fieldKey,
     * severity, creationType, triggeringEvidenceId, riskRatingDimension, arb, and text search.
     * Returns paginated results with sorting support.
     *
     * Special logic: When both arb AND assignedTo are provided, uses OR logic:
     *   - Show risks from my ARB OR assigned to me
     *   - Use case: Guild member wants to see all risks from their guild + any risks assigned to them from other guilds
     *
     * Used to replace old /api/risks/search endpoint functionality.
     */
    @Query("""
        SELECT ri FROM RiskItem ri
        WHERE (:appId IS NULL OR ri.appId = :appId)
        AND (:fieldKey IS NULL OR ri.fieldKey = :fieldKey)
        AND (:severity IS NULL OR ri.severity = :severity)
        AND (:triggeringEvidenceId IS NULL OR ri.triggeringEvidenceId = :triggeringEvidenceId)
        AND (:riskRatingDimension IS NULL OR ri.riskRatingDimension = :riskRatingDimension)
        AND (
            (:arb IS NOT NULL AND :assignedTo IS NOT NULL AND (ri.arb = :arb OR ri.assignedTo = :assignedTo))
            OR (:arb IS NOT NULL AND :assignedTo IS NULL AND ri.arb = :arb)
            OR (:arb IS NULL AND :assignedTo IS NOT NULL AND ri.assignedTo = :assignedTo)
            OR (:arb IS NULL AND :assignedTo IS NULL)
        )
        AND (:search IS NULL OR
             LOWER(COALESCE(ri.title, '')) LIKE :searchPattern OR
             LOWER(COALESCE(ri.description, '')) LIKE :searchPattern OR
             LOWER(COALESCE(ri.hypothesis, '')) LIKE :searchPattern OR
             LOWER(COALESCE(ri.condition, '')) LIKE :searchPattern OR
             LOWER(COALESCE(ri.consequence, '')) LIKE :searchPattern)
        AND (:status IS NULL OR ri.status IN :status)
        AND (:priority IS NULL OR ri.priority IN :priority)
        AND (:creationType IS NULL OR ri.creationType IN :creationType)
        """)
    Page<RiskItem> searchRiskItems(
        @Param("appId") String appId,
        @Param("assignedTo") String assignedTo,
        @Param("status") List<RiskItemStatus> status,
        @Param("priority") List<RiskPriority> priority,
        @Param("fieldKey") String fieldKey,
        @Param("severity") String severity,
        @Param("creationType") List<RiskCreationType> creationType,
        @Param("triggeringEvidenceId") String triggeringEvidenceId,
        @Param("riskRatingDimension") String riskRatingDimension,
        @Param("arb") String arb,
        @Param("search") String search,
        @Param("searchPattern") String searchPattern,
        Pageable pageable
    );

    // ============================================
    // APP-CENTRIC METRICS (Volume & Velocity)
    // ============================================

    /**
     * Count unique applications with CRITICAL priority items.
     * Used for "Critical Applications" app-centric metric.
     */
    @Query("""
        SELECT COUNT(DISTINCT ri.appId) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.priority = 'CRITICAL'
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countApplicationsWithCriticalItems(@Param("appIds") List<String> appIds);

    /**
     * Count unique applications with CRITICAL priority items (user-scoped for my-queue).
     */
    @Query("""
        SELECT COUNT(DISTINCT ri.appId) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.priority = 'CRITICAL'
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
        """)
    long countApplicationsWithCriticalItemsByUser(@Param("userId") String userId);

    /**
     * Count unique applications with OPEN status items (awaiting triage).
     * Used for "Applications Awaiting Triage" app-centric metric.
     */
    @Query("""
        SELECT COUNT(DISTINCT ri.appId) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.status = 'OPEN'
        """)
    long countApplicationsAwaitingTriage(@Param("appIds") List<String> appIds);

    /**
     * Count unique applications with OPEN status items (user-scoped for my-queue).
     */
    @Query("""
        SELECT COUNT(DISTINCT ri.appId) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.status = 'OPEN'
        """)
    long countApplicationsAwaitingTriageByUser(@Param("userId") String userId);

    /**
     * Count unique applications with new risks created in a time window.
     * Used for recent activity velocity metric (7-day and 30-day).
     */
    @Query("""
        SELECT COUNT(DISTINCT ri.appId) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.openedAt >= :since
        """)
    long countApplicationsWithNewRisks(
        @Param("appIds") List<String> appIds,
        @Param("since") java.time.OffsetDateTime since);

    /**
     * Count unique applications with new risks (user-scoped for my-queue).
     */
    @Query("""
        SELECT COUNT(DISTINCT ri.appId) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.openedAt >= :since
        """)
    long countApplicationsWithNewRisksByUser(
        @Param("userId") String userId,
        @Param("since") java.time.OffsetDateTime since);

    /**
     * Count unique applications with resolutions in a time window.
     * Used for recent activity velocity metric (7-day and 30-day).
     */
    @Query("""
        SELECT COUNT(DISTINCT ri.appId) FROM RiskItem ri
        WHERE ri.appId IN :appIds
        AND ri.resolvedAt >= :since
        AND ri.status IN ('RESOLVED', 'CLOSED')
        """)
    long countApplicationsWithResolutions(
        @Param("appIds") List<String> appIds,
        @Param("since") java.time.OffsetDateTime since);

    /**
     * Count unique applications with resolutions (user-scoped for my-queue).
     */
    @Query("""
        SELECT COUNT(DISTINCT ri.appId) FROM RiskItem ri
        WHERE ri.assignedTo = :userId
        AND ri.resolvedAt >= :since
        AND ri.status IN ('RESOLVED', 'CLOSED')
        """)
    long countApplicationsWithResolutionsByUser(
        @Param("userId") String userId,
        @Param("since") java.time.OffsetDateTime since);
}
