package com.example.gateway.risk.repository;

import com.example.gateway.risk.model.RiskItemStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for risk item status history tracking.
 * Provides query methods for audit trail and lifecycle analysis.
 */
@Repository
public interface RiskItemStatusHistoryRepository extends JpaRepository<RiskItemStatusHistory, String> {

    /**
     * Get complete status history for a risk item, ordered chronologically.
     *
     * @param riskItemId Risk item ID
     * @return List of status changes ordered by changed_at ASC
     */
    List<RiskItemStatusHistory> findByRiskItemIdOrderByChangedAtAsc(String riskItemId);

    /**
     * Get status history for a risk item, ordered reverse chronologically (most recent first).
     *
     * @param riskItemId Risk item ID
     * @return List of status changes ordered by changed_at DESC
     */
    List<RiskItemStatusHistory> findByRiskItemIdOrderByChangedAtDesc(String riskItemId);

    /**
     * Get most recent status change for a risk item.
     *
     * @param riskItemId Risk item ID
     * @return Most recent status change, or null if none exists
     */
    @Query("SELECT h FROM RiskItemStatusHistory h WHERE h.riskItemId = :riskItemId ORDER BY h.changedAt DESC LIMIT 1")
    RiskItemStatusHistory findMostRecentByRiskItemId(@Param("riskItemId") String riskItemId);

    /**
     * Get all status changes made by a specific user.
     *
     * @param changedBy User identifier
     * @return List of status changes made by this user
     */
    List<RiskItemStatusHistory> findByChangedByOrderByChangedAtDesc(String changedBy);

    /**
     * Get all status changes with a specific resolution type.
     * Useful for finding all SME approvals, rejections, etc.
     *
     * @param resolution Resolution type (e.g., "SME_APPROVED", "SME_REJECTED")
     * @return List of status changes with this resolution
     */
    List<RiskItemStatusHistory> findByResolutionOrderByChangedAtDesc(String resolution);

    /**
     * Get all status changes to a specific target status.
     * Useful for finding all risks that reached a certain state.
     *
     * @param toStatus Target status
     * @return List of status changes to this status
     */
    List<RiskItemStatusHistory> findByToStatusOrderByChangedAtDesc(String toStatus);

    /**
     * Get all status changes made by a specific actor role.
     *
     * @param actorRole Actor role (SME, PO, SYSTEM, ADMIN)
     * @return List of status changes by this role
     */
    List<RiskItemStatusHistory> findByActorRoleOrderByChangedAtDesc(String actorRole);

    /**
     * Count status transitions for a risk item.
     *
     * @param riskItemId Risk item ID
     * @return Number of status changes
     */
    long countByRiskItemId(String riskItemId);

    /**
     * Check if a risk item has ever been in a specific status.
     *
     * @param riskItemId Risk item ID
     * @param status Status to check
     * @return true if risk item was ever in this status
     */
    boolean existsByRiskItemIdAndToStatus(String riskItemId, String status);

    /**
     * Find all risk items that went through a specific status transition.
     * Example: Find all items that went from UNDER_SME_REVIEW to SME_APPROVED
     *
     * @param fromStatus Source status
     * @param toStatus Target status
     * @return List of status changes matching this transition
     */
    @Query("SELECT h FROM RiskItemStatusHistory h WHERE h.fromStatus = :fromStatus AND h.toStatus = :toStatus ORDER BY h.changedAt DESC")
    List<RiskItemStatusHistory> findByStatusTransition(
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus);

    /**
     * Get status history with pagination support.
     * Useful for large result sets.
     *
     * @param riskItemId Risk item ID
     * @param limit Maximum number of records
     * @return Limited list of status changes
     */
    @Query("SELECT h FROM RiskItemStatusHistory h WHERE h.riskItemId = :riskItemId ORDER BY h.changedAt DESC LIMIT :limit")
    List<RiskItemStatusHistory> findRecentByRiskItemId(
            @Param("riskItemId") String riskItemId,
            @Param("limit") int limit);
}
