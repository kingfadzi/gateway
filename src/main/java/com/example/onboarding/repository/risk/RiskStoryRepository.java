package com.example.onboarding.repository.risk;

import com.example.onboarding.model.risk.RiskStory;
import com.example.onboarding.model.RiskStatus;
import com.example.onboarding.model.RiskCreationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface RiskStoryRepository extends JpaRepository<RiskStory, String> {
    
    List<RiskStory> findByAppId(String appId);
    
    List<RiskStory> findByStatus(RiskStatus status);
    
    List<RiskStory> findByCreationType(RiskCreationType creationType);
    
    List<RiskStory> findByAssignedSme(String assignedSme);
    
    List<RiskStory> findByTriggeringEvidenceId(String triggeringEvidenceId);
    
    @Query("SELECT rs FROM RiskStory rs WHERE rs.appId = :appId AND rs.status IN :statuses")
    List<RiskStory> findByAppIdAndStatusIn(@Param("appId") String appId, @Param("statuses") List<RiskStatus> statuses);
    
    @Query("SELECT rs FROM RiskStory rs WHERE rs.assignedSme = :smeId AND rs.status IN :statuses ORDER BY rs.assignedAt ASC")
    List<RiskStory> findByAssignedSmeAndStatusInOrderByAssignedAt(@Param("smeId") String smeId, @Param("statuses") List<RiskStatus> statuses);
    
    @Query("SELECT rs FROM RiskStory rs WHERE rs.profileFieldId = :profileFieldId")
    List<RiskStory> findByProfileFieldId(@Param("profileFieldId") String profileFieldId);
    
    @Query("SELECT COUNT(rs) FROM RiskStory rs WHERE rs.assignedSme = :smeId AND rs.status = 'PENDING_SME_REVIEW'")
    long countPendingBySme(@Param("smeId") String smeId);
    
    boolean existsByAppIdAndFieldKeyAndTriggeringEvidenceId(String appId, String fieldKey, String triggeringEvidenceId);
    
    List<RiskStory> findByAppIdAndFieldKey(String appId, String fieldKey);
    
    // SME-specific queries for domain-based risk filtering
    List<RiskStory> findByAssignedSmeAndStatus(String assignedSme, RiskStatus status);
    
    @Query(value = """
        SELECT rs.*, 
               app.name, app.scope, app.parent_app_id, app.parent_app_name, app.business_service_name,
               app.app_criticality_assessment, app.security_rating, app.confidentiality_rating, 
               app.integrity_rating, app.availability_rating, app.resilience_rating,
               app.business_application_sys_id, app.architecture_hosting, app.jira_backlog_id,
               app.lean_control_service_id, app.repo_id, app.operational_status,
               app.transaction_cycle, app.transaction_cycle_id, app.application_type,
               app.application_tier, app.architecture_type, app.install_type, app.house_position,
               app.product_owner, app.product_owner_brid, app.system_architect, app.system_architect_brid,
               app.onboarding_status, app.owner_id, app.created_at as app_created_at, app.updated_at as app_updated_at,
               pf.derived_from as profile_derived_from
        FROM risk_story rs 
        LEFT JOIN application app ON rs.app_id = app.app_id
        LEFT JOIN profile_field pf ON rs.field_key = pf.field_key 
                                   AND rs.profile_id = pf.profile_id
        WHERE (:appId IS NULL OR rs.app_id = :appId)
          AND (:assignedSme IS NULL OR rs.assigned_sme = :assignedSme)  
          AND (:status IS NULL OR rs.status = :status)
          AND (:derivedFrom IS NULL OR pf.derived_from = :derivedFrom)
          AND (:fieldKey IS NULL OR rs.field_key = :fieldKey)
          AND (:severity IS NULL OR rs.severity = :severity)
          AND (:creationType IS NULL OR rs.creation_type = :creationType)
          AND (:triggeringEvidenceId IS NULL OR rs.triggering_evidence_id = :triggeringEvidenceId)
        ORDER BY 
          CASE WHEN :sortBy = 'assignedAt' AND :sortOrder = 'asc' THEN rs.assigned_at END ASC,
          CASE WHEN :sortBy = 'assignedAt' AND :sortOrder = 'desc' THEN rs.assigned_at END DESC,
          CASE WHEN :sortBy = 'createdAt' AND :sortOrder = 'asc' THEN rs.created_at END ASC,
          CASE WHEN :sortBy = 'createdAt' AND :sortOrder = 'desc' THEN rs.created_at END DESC,
          CASE WHEN :sortBy = 'openedAt' AND :sortOrder = 'asc' THEN rs.opened_at END ASC,
          CASE WHEN :sortBy = 'openedAt' AND :sortOrder = 'desc' THEN rs.opened_at END DESC,
          rs.assigned_at DESC
        LIMIT :size OFFSET :offset
    """, nativeQuery = true)
    List<Map<String, Object>> searchRisks(@Param("appId") String appId,
                                          @Param("assignedSme") String assignedSme, 
                                          @Param("status") String status,
                                          @Param("derivedFrom") String derivedFrom,
                                          @Param("fieldKey") String fieldKey,
                                          @Param("severity") String severity,
                                          @Param("creationType") String creationType,
                                          @Param("triggeringEvidenceId") String triggeringEvidenceId,
                                          @Param("sortBy") String sortBy,
                                          @Param("sortOrder") String sortOrder,
                                          @Param("size") int size,
                                          @Param("offset") int offset);
    
    /**
     * Find risks by app ID with pagination - returns raw result for flexible mapping
     */
    @Query(value = """
        SELECT rs.risk_id, rs.app_id, rs.field_key, rs.triggering_evidence_id, rs.creation_type,
               rs.assigned_sme, rs.title, rs.hypothesis, rs.condition, rs.consequence, 
               rs.severity, rs.status, rs.raised_by, rs.opened_at, rs.assigned_at,
               rs.policy_requirement_snapshot, rs.created_at, rs.updated_at,
               app.name as app_name, app.app_criticality_assessment
        FROM risk_story rs
        JOIN application app ON rs.app_id = app.app_id
        WHERE rs.app_id = :appId
        ORDER BY rs.created_at DESC
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<java.util.Map<String, Object>> findRisksByAppIdWithPagination(
        @Param("appId") String appId, 
        @Param("limit") int limit, 
        @Param("offset") int offset);
    
    /**
     * Count total risks for an application (for pagination)
     */
    @Query("SELECT COUNT(rs) FROM RiskStory rs WHERE rs.appId = :appId")
    long countByAppId(@Param("appId") String appId);
    
    /**
     * Count risks matching search criteria (for pagination total)
     */
    @Query(value = """
        SELECT COUNT(*) 
        FROM risk_story rs 
        LEFT JOIN application app ON rs.app_id = app.app_id
        LEFT JOIN profile_field pf ON rs.field_key = pf.field_key 
                                   AND rs.profile_id = pf.profile_id
        WHERE (:appId IS NULL OR rs.app_id = :appId)
          AND (:assignedSme IS NULL OR rs.assigned_sme = :assignedSme)  
          AND (:status IS NULL OR rs.status = :status)
          AND (:derivedFrom IS NULL OR pf.derived_from = :derivedFrom)
          AND (:fieldKey IS NULL OR rs.field_key = :fieldKey)
          AND (:severity IS NULL OR rs.severity = :severity)
          AND (:creationType IS NULL OR rs.creation_type = :creationType)
          AND (:triggeringEvidenceId IS NULL OR rs.triggering_evidence_id = :triggeringEvidenceId)
    """, nativeQuery = true)
    long countSearchResults(@Param("appId") String appId,
                           @Param("assignedSme") String assignedSme,
                           @Param("status") String status,
                           @Param("derivedFrom") String derivedFrom,
                           @Param("fieldKey") String fieldKey,
                           @Param("severity") String severity,
                           @Param("creationType") String creationType,
                           @Param("triggeringEvidenceId") String triggeringEvidenceId);
}
