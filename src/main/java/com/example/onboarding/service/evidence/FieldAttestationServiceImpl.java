package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.attestation.BulkAttestationRequest;
import com.example.onboarding.dto.attestation.BulkAttestationResponse;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;
import com.example.onboarding.config.FieldRegistryConfig;
import com.example.onboarding.repository.EvidenceFieldLinkRepository;
import com.example.onboarding.model.EvidenceFieldLink;
import com.example.onboarding.model.EvidenceFieldLinkStatus;
import dev.controlplane.auditkit.annotations.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FieldAttestationServiceImpl implements FieldAttestationService {
    
    private static final Logger log = LoggerFactory.getLogger(FieldAttestationServiceImpl.class);
    
    @Autowired
    private EvidenceFieldLinkRepository evidenceFieldLinkRepository;
    
    @Autowired
    private ProfileFieldRegistryService profileFieldRegistryService;
    
    @Autowired
    private EvidenceAttestationService evidenceAttestationService;

    @Override
    @Audited(action = "USER_ATTEST_EVIDENCE_FIELD", subjectType = "profile_field", subject = "#fieldRequest.profileFieldId()",
             context = {"appId=#appId", "fieldKey=#fieldRequest.fieldKey()", "attestedBy=#attestedBy"})
    public void processFieldAttestation(String appId,
                                      BulkAttestationRequest.FieldAttestationRequest fieldRequest,
                                      String attestedBy, 
                                      String comments,
                                      List<BulkAttestationResponse.AttestationSuccess> successful,
                                      List<BulkAttestationResponse.AttestationFailure> failed) {
        
        String profileFieldId = fieldRequest.profileFieldId();
        String fieldKey = fieldRequest.fieldKey();
        
        try {
            // 1. Check if field requires bulk attestation enabled
            if (!isBulkAttestationEnabledForField(appId, fieldKey)) {
                failed.add(new BulkAttestationResponse.AttestationFailure(
                    profileFieldId, fieldKey, "BULK_DISABLED",
                    "Bulk attestation not enabled for this field's criticality level"
                ));
                return;
            }
            
            // 2. Validate field exists in registry
            if (profileFieldRegistryService.getFieldTypeInfo(fieldKey).isEmpty()) {
                failed.add(new BulkAttestationResponse.AttestationFailure(
                    profileFieldId, fieldKey, "FIELD_NOT_FOUND",
                    "Field not found in registry"
                ));
                return;
            }
            
            // 3. Check if there's evidence attached to this profile field
            if (!hasEvidenceAttachedToProfileField(profileFieldId)) {
                failed.add(new BulkAttestationResponse.AttestationFailure(
                    profileFieldId, fieldKey, "NO_EVIDENCE",
                    "No evidence attached to profile field"
                ));
                return;
            }
            
            // 4. Process the attestation using external auditable service
            String attestationId = processAttestationForProfileField(profileFieldId, attestedBy, comments);
            
            successful.add(new BulkAttestationResponse.AttestationSuccess(
                profileFieldId, fieldKey, attestationId
            ));
            
            log.debug("Successfully attested field {} for app {} with attestation ID {}", 
                     fieldKey, appId, attestationId);
                     
        } catch (Exception e) {
            log.error("Error processing field attestation for profileFieldId={}: {}", 
                     profileFieldId, e.getMessage(), e);
            failed.add(new BulkAttestationResponse.AttestationFailure(
                profileFieldId, fieldKey, "PROCESSING_ERROR",
                "Error processing attestation: " + e.getMessage()
            ));
        }
    }
    
    private boolean isBulkAttestationEnabledForField(String appId, String fieldKey) {
        try {
            // For now, just return true since we don't have criticality info readily available
            // This can be enhanced later with proper criticality mapping
            return true;
            
        } catch (Exception e) {
            log.error("Error checking bulk attestation eligibility for field {}: {}", fieldKey, e.getMessage());
            return false;
        }
    }
    
    private boolean hasEvidenceAttachedToProfileField(String profileFieldId) {
        return evidenceFieldLinkRepository.findByProfileFieldId(profileFieldId)
            .stream()
            .anyMatch(link -> link.getLinkStatus() != EvidenceFieldLinkStatus.REJECTED);
    }
    
    @Transactional
    private String processAttestationForProfileField(String profileFieldId, String attestedBy, String comments) {
        List<EvidenceFieldLink> links = evidenceFieldLinkRepository.findByProfileFieldId(profileFieldId);
        
        if (links.isEmpty()) {
            throw new RuntimeException("No evidence links found for profile field: " + profileFieldId);
        }
        
        // Generate attestation ID
        String attestationId = "att_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        // Process attestation for each evidence link using external auditable service
        for (EvidenceFieldLink link : links) {
            evidenceAttestationService.attestEvidenceFieldLink(
                link.getEvidenceId(), profileFieldId, attestedBy, comments, "bulk"
            );
        }
        
        return attestationId;
    }
}