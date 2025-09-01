package com.example.onboarding.repository.risk;

import com.example.onboarding.model.risk.RiskStory;
import com.example.onboarding.model.RiskStatus;
import com.example.onboarding.model.RiskCreationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
