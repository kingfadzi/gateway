package com.example.gateway.risk.repository;

import com.example.gateway.risk.model.DomainRisk;
import com.example.gateway.risk.model.DomainRiskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for domain-level risk aggregations.
 * Provides queries optimized for ARB (Architecture Review Board) workflows.
 */
public interface DomainRiskRepository extends JpaRepository<DomainRisk, String> {

    /**
     * Find domain risk by application and risk rating dimension.
     * Used to get or create domain risk for a specific app+risk_rating_dimension combination.
     */
    Optional<DomainRisk> findByAppIdAndRiskRatingDimension(String appId, String riskRatingDimension);

    /**
     * Find all domain risks assigned to a specific ARB
     */
    List<DomainRisk> findByAssignedArb(String arb);

    /**
     * Find domain risks by ARB and status
     */
    List<DomainRisk> findByAssignedArbAndStatus(String arb, DomainRiskStatus status);

    /**
     * Find domain risks by ARB and multiple statuses, prioritized by score and open items.
     * Used for ARB workbench view.
     */
    @Query("""
        SELECT dr FROM DomainRisk dr
        WHERE dr.assignedArb = :arb
        AND dr.status IN :statuses
        ORDER BY dr.priorityScore DESC, dr.openItems DESC
        """)
    List<DomainRisk> findByArbPrioritized(
        @Param("arb") String arb,
        @Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Get risk rating dimension summary for an ARB.
     * Returns: [risk_rating_dimension, count, total_open_items, avg_priority_score]
     * Used for ARB dashboard/overview.
     */
    @Query("""
        SELECT dr.riskRatingDimension, COUNT(dr), SUM(dr.openItems), AVG(dr.priorityScore)
        FROM DomainRisk dr
        WHERE dr.assignedArb = :arb
        AND dr.status IN :statuses
        GROUP BY dr.riskRatingDimension
        ORDER BY AVG(dr.priorityScore) DESC
        """)
    List<Object[]> getDomainSummaryForArb(
        @Param("arb") String arb,
        @Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Find all domain risks for an application
     */
    List<DomainRisk> findByAppId(String appId);

    /**
     * Find domain risks by status
     */
    List<DomainRisk> findByStatus(DomainRiskStatus status);

    /**
     * Count domain risks by ARB and status
     */
    long countByAssignedArbAndStatus(String arb, DomainRiskStatus status);

    /**
     * Find domain risks with high priority items that need attention
     */
    @Query("""
        SELECT dr FROM DomainRisk dr
        WHERE dr.highPriorityItems > 0
        AND dr.status IN :statuses
        ORDER BY dr.priorityScore DESC, dr.highPriorityItems DESC
        """)
    List<DomainRisk> findHighPriorityDomainRisks(
        @Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Find domain risks assigned to a specific user (my-queue scope).
     * Returns domain risks where assigned_to matches userId and status is active.
     */
    @Query("""
        SELECT dr FROM DomainRisk dr
        WHERE dr.assignedTo = :userId
        AND dr.status IN :statuses
        ORDER BY dr.priorityScore DESC, dr.openItems DESC
        """)
    List<DomainRisk> findByUserAndStatuses(
        @Param("userId") String userId,
        @Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Find domain risks by risk rating dimension (my-dimension scope).
     * Returns all domain risks in a specific risk rating dimension with active statuses.
     */
    @Query("""
        SELECT dr FROM DomainRisk dr
        WHERE dr.riskRatingDimension = :riskRatingDimension
        AND dr.status IN :statuses
        ORDER BY dr.priorityScore DESC, dr.openItems DESC
        """)
    List<DomainRisk> findByRiskRatingDimensionAndStatuses(
        @Param("riskRatingDimension") String riskRatingDimension,
        @Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Find all domain risks with active statuses (all-domains scope).
     */
    @Query("""
        SELECT dr FROM DomainRisk dr
        WHERE dr.status IN :statuses
        ORDER BY dr.priorityScore DESC, dr.openItems DESC
        """)
    List<DomainRisk> findAllByStatuses(@Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Find domain risks by app IDs.
     * Used for batch loading to avoid N+1 queries.
     */
    @Query("""
        SELECT dr FROM DomainRisk dr
        WHERE dr.appId IN :appIds
        AND dr.status IN :statuses
        ORDER BY dr.appId, dr.priorityScore DESC
        """)
    List<DomainRisk> findByAppIdsAndStatuses(
        @Param("appIds") List<String> appIds,
        @Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Count domain risks with PENDING_ARB_REVIEW status.
     * Used for dashboard metrics pending review count.
     */
    @Query("""
        SELECT COUNT(dr) FROM DomainRisk dr
        WHERE dr.status = 'PENDING_ARB_REVIEW'
        """)
    long countPendingReview();

    /**
     * Count domain risks with PENDING_ARB_REVIEW status for specific apps.
     * Scoped version for dashboard metrics (my-queue, my-domain, all-domains).
     */
    @Query("""
        SELECT COUNT(dr) FROM DomainRisk dr
        WHERE dr.appId IN :appIds
        AND dr.status = 'PENDING_ARB_REVIEW'
        """)
    long countPendingReviewByAppIds(@Param("appIds") List<String> appIds);

    /**
     * Get unique app IDs from domain risks matching scope criteria.
     * Used to fetch applications for dashboard.
     */
    @Query("""
        SELECT DISTINCT dr.appId FROM DomainRisk dr
        WHERE dr.assignedTo = :userId
        AND dr.status IN :statuses
        """)
    List<String> findAppIdsByUser(
        @Param("userId") String userId,
        @Param("statuses") List<DomainRiskStatus> statuses);

    @Query("""
        SELECT DISTINCT dr.appId FROM DomainRisk dr
        WHERE dr.assignedArb = :arbName
        AND dr.status IN :statuses
        """)
    List<String> findAppIdsByDomain(
        @Param("arbName") String arbName,
        @Param("statuses") List<DomainRiskStatus> statuses);

    @Query("""
        SELECT DISTINCT dr.appId FROM DomainRisk dr
        WHERE dr.status IN :statuses
        """)
    List<String> findAllAppIdsByStatuses(@Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Find domain risks by app ID and assigned ARB.
     * Used for assignment operations.
     */
    List<DomainRisk> findByAppIdAndAssignedArb(String appId, String assignedArb);

    /**
     * Update assigned_to fields for all domain risks matching app+ARB.
     * Used for assigning applications to ARB members.
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE DomainRisk dr
        SET dr.assignedTo = :assignedTo,
            dr.assignedToName = :assignedToName,
            dr.assignedAt = :assignedAt,
            dr.updatedAt = :assignedAt
        WHERE dr.appId = :appId
        AND dr.assignedArb = :arbName
        """)
    int updateAssignedToForAppAndArb(
        @Param("appId") String appId,
        @Param("arbName") String arbName,
        @Param("assignedTo") String assignedTo,
        @Param("assignedToName") String assignedToName,
        @Param("assignedAt") OffsetDateTime assignedAt);

    // ========== APP-CENTRIC METRICS QUERIES ==========
    // Used for ARB dashboard overview metrics (counting applications, not risk items)

    /**
     * Count unique applications at risk for an ARB.
     * Returns count of distinct apps with domain risks in active statuses.
     */
    @Query("""
        SELECT COUNT(DISTINCT dr.appId) FROM DomainRisk dr
        WHERE dr.assignedArb = :arbName
        AND dr.status IN :statuses
        """)
    long countApplicationsAtRisk(
        @Param("arbName") String arbName,
        @Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Count unique applications at risk (scoped by app IDs).
     * Used for my-queue and filtered views.
     */
    @Query("""
        SELECT COUNT(DISTINCT dr.appId) FROM DomainRisk dr
        WHERE dr.appId IN :appIds
        AND dr.status IN :statuses
        """)
    long countApplicationsAtRiskByAppIds(
        @Param("appIds") List<String> appIds,
        @Param("statuses") List<DomainRiskStatus> statuses);

    /**
     * Count applications with high priority scores (≥70).
     * Used for "High Risk Applications" metric.
     */
    @Query("""
        SELECT COUNT(DISTINCT dr.appId) FROM DomainRisk dr
        WHERE dr.assignedArb = :arbName
        AND dr.status IN :statuses
        AND dr.priorityScore >= :scoreThreshold
        """)
    long countHighRiskApplications(
        @Param("arbName") String arbName,
        @Param("statuses") List<DomainRiskStatus> statuses,
        @Param("scoreThreshold") double scoreThreshold);

    /**
     * Count applications with high priority scores (scoped by app IDs).
     */
    @Query("""
        SELECT COUNT(DISTINCT dr.appId) FROM DomainRisk dr
        WHERE dr.appId IN :appIds
        AND dr.status IN :statuses
        AND dr.priorityScore >= :scoreThreshold
        """)
    long countHighRiskApplicationsByAppIds(
        @Param("appIds") List<String> appIds,
        @Param("statuses") List<DomainRiskStatus> statuses,
        @Param("scoreThreshold") double scoreThreshold);

    /**
     * Get application risk level distribution.
     * Returns: [risk_level, app_count]
     * Risk levels: CRITICAL (90-100), HIGH (70-89), MEDIUM (40-69), LOW (0-39)
     */
    @Query(value = """
        SELECT risk_level, COUNT(DISTINCT app_id)
        FROM (
            SELECT
                dr.app_id,
                CASE
                    WHEN MAX(dr.priority_score) >= 90 THEN 'CRITICAL'
                    WHEN MAX(dr.priority_score) >= 70 THEN 'HIGH'
                    WHEN MAX(dr.priority_score) >= 40 THEN 'MEDIUM'
                    ELSE 'LOW'
                END as risk_level
            FROM domain_risk dr
            WHERE dr.assigned_arb = :arbName
            AND dr.status IN (:statuses)
            GROUP BY dr.app_id
        ) subquery
        GROUP BY risk_level
        ORDER BY
            CASE risk_level
                WHEN 'CRITICAL' THEN 1
                WHEN 'HIGH' THEN 2
                WHEN 'MEDIUM' THEN 3
                WHEN 'LOW' THEN 4
            END
        """, nativeQuery = true)
    List<Object[]> getApplicationRiskLevelDistribution(
        @Param("arbName") String arbName,
        @Param("statuses") List<String> statuses);

    /**
     * Get application risk level distribution (scoped by app IDs).
     */
    @Query(value = """
        SELECT risk_level, COUNT(DISTINCT app_id)
        FROM (
            SELECT
                dr.app_id,
                CASE
                    WHEN MAX(dr.priority_score) >= 90 THEN 'CRITICAL'
                    WHEN MAX(dr.priority_score) >= 70 THEN 'HIGH'
                    WHEN MAX(dr.priority_score) >= 40 THEN 'MEDIUM'
                    ELSE 'LOW'
                END as risk_level
            FROM domain_risk dr
            WHERE dr.app_id IN (:appIds)
            AND dr.status IN (:statuses)
            GROUP BY dr.app_id
        ) subquery
        GROUP BY risk_level
        ORDER BY
            CASE risk_level
                WHEN 'CRITICAL' THEN 1
                WHEN 'HIGH' THEN 2
                WHEN 'MEDIUM' THEN 3
                WHEN 'LOW' THEN 4
            END
        """, nativeQuery = true)
    List<Object[]> getApplicationRiskLevelDistributionByAppIds(
        @Param("appIds") List<String> appIds,
        @Param("statuses") List<String> statuses);
}
