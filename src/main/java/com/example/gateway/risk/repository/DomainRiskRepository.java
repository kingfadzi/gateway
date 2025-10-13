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
     * Find domain risk by application and domain.
     * Used to get or create domain risk for a specific app+domain combination.
     */
    Optional<DomainRisk> findByAppIdAndDomain(String appId, String domain);

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
     * Get domain-level summary for an ARB.
     * Returns: [domain, count, total_open_items, avg_priority_score]
     * Used for ARB dashboard/overview.
     */
    @Query("""
        SELECT dr.domain, COUNT(dr), SUM(dr.openItems), AVG(dr.priorityScore)
        FROM DomainRisk dr
        WHERE dr.assignedArb = :arb
        AND dr.status IN :statuses
        GROUP BY dr.domain
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
     * Find domain risks by domain (my-domain scope).
     * Returns all domain risks in a specific domain with active statuses.
     */
    @Query("""
        SELECT dr FROM DomainRisk dr
        WHERE dr.domain = :domain
        AND dr.status IN :statuses
        ORDER BY dr.priorityScore DESC, dr.openItems DESC
        """)
    List<DomainRisk> findByDomainAndStatuses(
        @Param("domain") String domain,
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
        WHERE dr.domain = :domain
        AND dr.status IN :statuses
        """)
    List<String> findAppIdsByDomain(
        @Param("domain") String domain,
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
}
