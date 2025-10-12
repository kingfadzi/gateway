package com.example.gateway.risk.repository;

import com.example.gateway.risk.model.RiskItem;
import com.example.gateway.risk.model.RiskItemStatus;
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
}
