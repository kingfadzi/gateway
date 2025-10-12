package com.example.gateway.risk.repository;

import com.example.gateway.risk.model.RiskComment;
import com.example.gateway.risk.model.RiskCommentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for risk comments.
 */
@Repository
public interface RiskCommentRepository extends JpaRepository<RiskComment, String> {

    /**
     * Get all comments for a risk item, ordered by time (newest first).
     */
    @Query("SELECT rc FROM RiskComment rc " +
           "WHERE rc.riskItemId = :riskItemId " +
           "ORDER BY rc.commentedAt DESC")
    List<RiskComment> findByRiskItemIdOrderByCommentedAtDesc(@Param("riskItemId") String riskItemId);

    /**
     * Get comments for a risk item filtered by type.
     */
    @Query("SELECT rc FROM RiskComment rc " +
           "WHERE rc.riskItemId = :riskItemId " +
           "AND rc.commentType = :commentType " +
           "ORDER BY rc.commentedAt DESC")
    List<RiskComment> findByRiskItemIdAndCommentType(
            @Param("riskItemId") String riskItemId,
            @Param("commentType") RiskCommentType commentType);

    /**
     * Get only public comments (not internal ARB notes).
     */
    @Query("SELECT rc FROM RiskComment rc " +
           "WHERE rc.riskItemId = :riskItemId " +
           "AND rc.isInternal = false " +
           "ORDER BY rc.commentedAt DESC")
    List<RiskComment> findPublicCommentsByRiskItemId(@Param("riskItemId") String riskItemId);

    /**
     * Get all comments by a specific user.
     */
    @Query("SELECT rc FROM RiskComment rc " +
           "WHERE rc.commentedBy = :commentedBy " +
           "ORDER BY rc.commentedAt DESC")
    List<RiskComment> findByCommentedBy(@Param("commentedBy") String commentedBy);

    /**
     * Count comments for a risk item.
     */
    @Query("SELECT COUNT(rc) FROM RiskComment rc WHERE rc.riskItemId = :riskItemId")
    long countByRiskItemId(@Param("riskItemId") String riskItemId);
}
