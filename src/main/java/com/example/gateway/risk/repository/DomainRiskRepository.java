package com.example.gateway.risk.repository;

import com.example.gateway.risk.model.DomainRisk;
import com.example.gateway.risk.model.DomainRiskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
