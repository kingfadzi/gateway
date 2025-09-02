package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.AttachEvidenceToFieldRequest;
import com.example.onboarding.dto.evidence.EvidenceFieldLinkResponse;
import com.example.onboarding.dto.risk.AutoRiskCreationResponse;
import com.example.onboarding.exception.DataIntegrityException;
import com.example.onboarding.exception.NotFoundException;
import com.example.onboarding.model.EvidenceFieldLink;
import com.example.onboarding.model.EvidenceFieldLinkId;
import com.example.onboarding.model.EvidenceFieldLinkStatus;
import com.example.onboarding.repository.EvidenceFieldLinkRepository;
import com.example.onboarding.repository.evidence.EvidenceRepository;
import com.example.onboarding.repository.application.ApplicationManagementRepository;
import com.example.onboarding.service.risk.RiskAutoCreationService;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;
import com.example.onboarding.config.FieldRegistryConfig;
import dev.controlplane.auditkit.annotations.Audited;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EvidenceFieldLinkServiceImpl implements EvidenceFieldLinkService {

    private final EvidenceFieldLinkRepository evidenceFieldLinkRepository;
    private final RiskAutoCreationService riskAutoCreationService;
    private final EvidenceRepository evidenceRepository;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    private final ApplicationManagementRepository applicationRepository;
    private final FieldRegistryConfig fieldRegistryConfig;
    private final NamedParameterJdbcTemplate namedJdbc;

    public EvidenceFieldLinkServiceImpl(EvidenceFieldLinkRepository evidenceFieldLinkRepository,
                                       RiskAutoCreationService riskAutoCreationService,
                                       EvidenceRepository evidenceRepository,
                                       ProfileFieldRegistryService profileFieldRegistryService,
                                       ApplicationManagementRepository applicationRepository,
                                       FieldRegistryConfig fieldRegistryConfig,
                                       NamedParameterJdbcTemplate namedJdbc) {
        this.evidenceFieldLinkRepository = evidenceFieldLinkRepository;
        this.riskAutoCreationService = riskAutoCreationService;
        this.evidenceRepository = evidenceRepository;
        this.profileFieldRegistryService = profileFieldRegistryService;
        this.applicationRepository = applicationRepository;
        this.fieldRegistryConfig = fieldRegistryConfig;
        this.namedJdbc = namedJdbc;
    }

    @Override
    @Transactional
    @Audited(action = "CREATE_ATTESTATION_REQUEST", subjectType = "profile_field", subject = "#profileFieldId",
             context = {"evidenceId=#evidenceId", "appId=#appId"})
    public EvidenceFieldLinkResponse attachEvidenceToField(String evidenceId, String profileFieldId, 
                                                          String appId, AttachEvidenceToFieldRequest request) {
        
        // Verify evidence exists
        evidenceRepository.findEvidenceById(evidenceId)
                .orElseThrow(() -> new NotFoundException("Evidence not found with id: " + evidenceId));

        EvidenceFieldLinkId id = new EvidenceFieldLinkId(evidenceId, profileFieldId);
        
        // Check if link already exists
        if (evidenceFieldLinkRepository.existsById(id)) {
            throw new DataIntegrityException("EvidenceFieldLinkService", evidenceId, "profileFieldId", 
                                           profileFieldId, "Evidence already linked to this field.");
        }

        // Determine appropriate link status based on field criticality
        String fieldKey = getFieldKeyFromProfileFieldId(profileFieldId, appId);
        boolean requiresReview = checkIfFieldRequiresReview(appId, fieldKey);
        
        EvidenceFieldLinkStatus linkStatus = requiresReview 
            ? EvidenceFieldLinkStatus.PENDING_SME_REVIEW 
            : EvidenceFieldLinkStatus.PENDING_PO_REVIEW;

        // Create the link
        EvidenceFieldLink link = new EvidenceFieldLink();
        link.setEvidenceId(evidenceId);
        link.setProfileFieldId(profileFieldId);
        link.setAppId(appId);
        link.setLinkStatus(linkStatus);
        link.setLinkedBy(request.linkedBy());
        link.setLinkedAt(OffsetDateTime.now());
        // Note: comment is stored in reviewComment field during review process
        link.setCreatedAt(OffsetDateTime.now());
        link.setUpdatedAt(OffsetDateTime.now());

        EvidenceFieldLink savedLink = evidenceFieldLinkRepository.save(link);

        // Evaluate auto-risk creation
        AutoRiskCreationResponse riskResponse = evaluateAndCreateRisk(evidenceId, profileFieldId, appId);
        
        if (riskResponse.wasCreated()) {
            return EvidenceFieldLinkResponse.fromEntityWithRisk(savedLink, 
                                                               riskResponse.riskId(), 
                                                               riskResponse.assignedSme());
        } else {
            return EvidenceFieldLinkResponse.fromEntity(savedLink);
        }
    }

    @Override
    @Transactional
    public void detachEvidenceFromField(String evidenceId, String profileFieldId) {
        EvidenceFieldLinkId id = new EvidenceFieldLinkId(evidenceId, profileFieldId);
        
        if (!evidenceFieldLinkRepository.existsById(id)) {
            throw new NotFoundException("Evidence field link not found.");
        }
        
        evidenceFieldLinkRepository.deleteById(id);
    }

    @Override
    @Transactional
    public EvidenceFieldLinkResponse reviewEvidenceFieldLink(String evidenceId, String profileFieldId, 
                                                            String reviewedBy, String comment, boolean approved) {
        EvidenceFieldLinkId id = new EvidenceFieldLinkId(evidenceId, profileFieldId);
        
        EvidenceFieldLink link = evidenceFieldLinkRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Evidence field link not found."));

        // Update review information
        link.setLinkStatus(approved ? EvidenceFieldLinkStatus.APPROVED : EvidenceFieldLinkStatus.REJECTED);
        link.setReviewedBy(reviewedBy);
        link.setReviewedAt(OffsetDateTime.now());
        link.setReviewComment(comment);
        link.setUpdatedAt(OffsetDateTime.now());

        EvidenceFieldLink savedLink = evidenceFieldLinkRepository.save(link);
        return EvidenceFieldLinkResponse.fromEntity(savedLink);
    }

    @Override
    public List<EvidenceFieldLinkResponse> getEvidenceFieldLinks(String evidenceId) {
        List<EvidenceFieldLink> links = evidenceFieldLinkRepository.findByEvidenceId(evidenceId);
        return links.stream()
                .map(EvidenceFieldLinkResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvidenceFieldLinkResponse> getFieldEvidenceLinks(String profileFieldId) {
        List<EvidenceFieldLink> links = evidenceFieldLinkRepository.findByProfileFieldId(profileFieldId);
        return links.stream()
                .map(EvidenceFieldLinkResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<EvidenceFieldLinkResponse> getEvidenceFieldLink(String evidenceId, String profileFieldId) {
        EvidenceFieldLinkId id = new EvidenceFieldLinkId(evidenceId, profileFieldId);
        return evidenceFieldLinkRepository.findById(id)
                .map(EvidenceFieldLinkResponse::fromEntity);
    }

    @Override
    public AutoRiskCreationResponse evaluateAndCreateRisk(String evidenceId, String profileFieldId, String appId) {
        return riskAutoCreationService.evaluateAndCreateRisk(evidenceId, profileFieldId, appId);
    }
    
    /**
     * Get field key from profile field ID
     */
    private String getFieldKeyFromProfileFieldId(String profileFieldId, String appId) {
        try {
            // Query profile_field table to get field_key
            String sql = "SELECT field_key FROM profile_field WHERE id = :profileFieldId";
            
            return namedJdbc.queryForObject(sql, 
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("profileFieldId", profileFieldId), 
                String.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not find field key for profile field: " + profileFieldId, e);
        }
    }
    
    /**
     * Check if field requires review based on the policy requirements
     */
    private boolean checkIfFieldRequiresReview(String appId, String fieldKey) {
        try {
            // Get the rating value for this field's derived_from column
            String derivedFrom = getDerivedFromForField(fieldKey);
            if (derivedFrom == null) {
                return true; // Default to requiring review for safety
            }
            
            String rating = applicationRepository.getApplicationRatingByColumn(appId, derivedFrom);
            if (rating == null) {
                return true; // Default to requiring review for safety
            }
            
            // Get field configuration from registry
            FieldRegistryConfig.FieldDefinition fieldDef = fieldRegistryConfig.getRegistry().fields.stream()
                .filter(field -> field.key.equals(fieldKey))
                .findFirst()
                .orElse(null);
                
            if (fieldDef == null || fieldDef.rule == null) {
                return true; // Default to requiring review for safety
            }
            
            // Get the specific rule for this rating
            Object ruleForRating = fieldDef.rule.get(rating);
            if (ruleForRating instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> ruleMap = (java.util.Map<String, Object>) ruleForRating;
                Object requiresReview = ruleMap.get("requires_review");
                
                if (requiresReview instanceof Boolean) {
                    return (Boolean) requiresReview;
                }
            }
            
            // Default to requiring review if not specified
            return true;
            
        } catch (Exception e) {
            // Default to requiring review for safety
            return true;
        }
    }
    
    /**
     * Get derived_from field for a field key from registry
     */
    private String getDerivedFromForField(String fieldKey) {
        FieldRegistryConfig.FieldDefinition fieldDef = fieldRegistryConfig.getRegistry().fields.stream()
            .filter(field -> field.key.equals(fieldKey))
            .findFirst()
            .orElse(null);
            
        return fieldDef != null ? fieldDef.derivedFrom : null;
    }
}