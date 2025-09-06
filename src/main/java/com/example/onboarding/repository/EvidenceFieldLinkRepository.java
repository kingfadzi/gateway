package com.example.onboarding.repository;

import com.example.onboarding.model.EvidenceFieldLink;
import com.example.onboarding.model.EvidenceFieldLinkId;
import com.example.onboarding.model.EvidenceFieldLinkStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvidenceFieldLinkRepository extends JpaRepository<EvidenceFieldLink, EvidenceFieldLinkId> {
    
    List<EvidenceFieldLink> findByEvidenceId(String evidenceId);
    
    List<EvidenceFieldLink> findByProfileFieldId(String profileFieldId);
    
    List<EvidenceFieldLink> findByAppId(String appId);
    
    List<EvidenceFieldLink> findByLinkStatus(EvidenceFieldLinkStatus status);
    
    @Query("SELECT efl FROM EvidenceFieldLink efl WHERE efl.appId = :appId AND efl.profileFieldId = :profileFieldId")
    List<EvidenceFieldLink> findByAppIdAndProfileFieldId(@Param("appId") String appId, @Param("profileFieldId") String profileFieldId);
    
    @Query("SELECT efl FROM EvidenceFieldLink efl WHERE efl.evidenceId = :evidenceId AND efl.linkStatus = :status")
    List<EvidenceFieldLink> findByEvidenceIdAndStatus(@Param("evidenceId") String evidenceId, @Param("status") EvidenceFieldLinkStatus status);
    
    @Query("SELECT efl FROM EvidenceFieldLink efl WHERE efl.profileFieldId = :profileFieldId AND efl.linkStatus = :status")
    List<EvidenceFieldLink> findByProfileFieldIdAndStatus(@Param("profileFieldId") String profileFieldId, @Param("status") EvidenceFieldLinkStatus status);
}