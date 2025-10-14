package com.example.gateway.risk.repository;

import com.example.gateway.risk.model.RiskItemAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for risk item assignment history.
 * Provides audit trail of assignment changes.
 */
public interface RiskItemAssignmentHistoryRepository extends JpaRepository<RiskItemAssignmentHistory, String> {

    /**
     * Get assignment history for a risk item, ordered by most recent first.
     */
    List<RiskItemAssignmentHistory> findByRiskItemIdOrderByAssignedAtDesc(String riskItemId);

    /**
     * Get all assignments for a user (audit trail), ordered by most recent first.
     */
    List<RiskItemAssignmentHistory> findByAssignedToOrderByAssignedAtDesc(String userId);

    /**
     * Get assignments made by a specific user, ordered by most recent first.
     */
    List<RiskItemAssignmentHistory> findByAssignedByOrderByAssignedAtDesc(String userId);
}
