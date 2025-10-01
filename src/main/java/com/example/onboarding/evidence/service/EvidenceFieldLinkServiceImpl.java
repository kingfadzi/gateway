package com.example.onboarding.evidence.service;

import com.example.onboarding.evidence.dto.AttachEvidenceToFieldRequest;
import com.example.onboarding.evidence.dto.EvidenceFieldLinkResponse;
import com.example.onboarding.risk.dto.AutoRiskCreationResponse;
import com.example.onboarding.application.dto.attestation.BulkAttestationRequest;
import com.example.onboarding.application.dto.attestation.BulkAttestationResponse;
import com.example.onboarding.application.dto.attestation.IndividualAttestationRequest;
import com.example.onboarding.application.dto.attestation.IndividualAttestationResponse;
import com.example.onboarding.application.dto.exception.DataIntegrityException;
import com.example.onboarding.application.dto.exception.NotFoundException;
import com.example.onboarding.evidence.model.EvidenceFieldLink;
import com.example.onboarding.evidence.model.EvidenceFieldLinkId;
import com.example.onboarding.evidence.model.EvidenceFieldLinkStatus;
import com.example.onboarding.evidence.repository.EvidenceFieldLinkRepository;
import com.example.onboarding.evidence.repository.EvidenceRepository;
import com.example.onboarding.application.repository.ApplicationManagementRepository;
import com.example.onboarding.risk.service.RiskAutoCreationService;
import com.example.onboarding.profile.service.ProfileFieldRegistryService;
import com.example.onboarding.config.FieldRegistryConfig;
import dev.controlplane.auditkit.annotations.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EvidenceFieldLinkServiceImpl implements EvidenceFieldLinkService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceFieldLinkServiceImpl.class);
    
    private final EvidenceFieldLinkRepository evidenceFieldLinkRepository;
    private final RiskAutoCreationService riskAutoCreationService;
    private final EvidenceRepository evidenceRepository;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    private final ApplicationManagementRepository applicationRepository;
    private final FieldRegistryConfig fieldRegistryConfig;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final FieldAttestationService fieldAttestationService;
    private final EvidenceAttestationService evidenceAttestationService;
    public EvidenceFieldLinkServiceImpl(EvidenceFieldLinkRepository evidenceFieldLinkRepository,
                                       RiskAutoCreationService riskAutoCreationService,
                                       EvidenceRepository evidenceRepository,
                                       ProfileFieldRegistryService profileFieldRegistryService,
                                       ApplicationManagementRepository applicationRepository,
                                       FieldRegistryConfig fieldRegistryConfig,
                                       NamedParameterJdbcTemplate namedJdbc,
                                       FieldAttestationService fieldAttestationService,
                                       EvidenceAttestationService evidenceAttestationService) {
        this.evidenceFieldLinkRepository = evidenceFieldLinkRepository;
        this.riskAutoCreationService = riskAutoCreationService;
        this.evidenceRepository = evidenceRepository;
        this.profileFieldRegistryService = profileFieldRegistryService;
        this.applicationRepository = applicationRepository;
        this.fieldRegistryConfig = fieldRegistryConfig;
        this.namedJdbc = namedJdbc;
        this.fieldAttestationService = fieldAttestationService;
        this.evidenceAttestationService = evidenceAttestationService;
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

        // Only evaluate auto-risk creation if field requires review (SME workflow)
        if (requiresReview) {
            AutoRiskCreationResponse riskResponse = evaluateAndCreateRisk(evidenceId, profileFieldId, appId);
            
            if (riskResponse.wasCreated()) {
                return EvidenceFieldLinkResponse.fromEntityWithRisk(savedLink, 
                                                                   riskResponse.riskId(), 
                                                                   riskResponse.assignedSme());
            }
        }
        
        // For PO review workflow (requiresReview = false) or when no risk was created
        return EvidenceFieldLinkResponse.fromEntity(savedLink);
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
    @Transactional
    public EvidenceFieldLinkResponse userAttestEvidenceFieldLink(String evidenceId, String profileFieldId,
                                                               String attestedBy, String comment) {
        log.debug("AUDIT DEBUG: userAttestEvidenceFieldLink called with evidenceId={}, profileFieldId={}, attestedBy={}", 
                 evidenceId, profileFieldId, attestedBy);
        
        EvidenceFieldLinkId id = new EvidenceFieldLinkId(evidenceId, profileFieldId);
        
        EvidenceFieldLink link = evidenceFieldLinkRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Evidence field link not found."));

        // Update with user attestation
        link.setLinkStatus(EvidenceFieldLinkStatus.USER_ATTESTED);
        link.setReviewedBy(attestedBy);
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
    
    private String generateAttestationId(String evidenceId, String profileFieldId) {
        // Generate a consistent attestation ID based on evidence and profile field
        // This allows for idempotent operations and easy tracking
        return "att_" + evidenceId.substring(evidenceId.length() - 8) + 
               "_" + profileFieldId.substring(profileFieldId.length() - 8);
    }
    
    @Override
    @Transactional
    public BulkAttestationResponse processBulkAttestations(String appId, String attestedBy, BulkAttestationRequest request) {
        log.debug("Processing bulk attestations for app {} with {} fields by {} (type: {})", 
                 appId, request.fields().size(), attestedBy, request.attestationType());
        
        List<BulkAttestationResponse.AttestationSuccess> successful = new ArrayList<>();
        List<BulkAttestationResponse.AttestationFailure> failed = new ArrayList<>();
        
        for (BulkAttestationRequest.FieldAttestationRequest fieldRequest : request.fields()) {
            try {
                // Use attestationComments if provided, otherwise fall back to comments
                String commentsToUse = request.attestationComments() != null ? 
                    request.attestationComments() : request.comments();
                fieldAttestationService.processFieldAttestation(appId, fieldRequest, attestedBy, 
                                      commentsToUse, successful, failed);
            } catch (Exception e) {
                log.error("Unexpected error processing field {}: {}", fieldRequest.profileFieldId(), e.getMessage(), e);
                failed.add(new BulkAttestationResponse.AttestationFailure(
                    fieldRequest.profileFieldId(),
                    fieldRequest.fieldKey(),
                    "PROCESSING_ERROR",
                    "Unexpected error: " + e.getMessage()
                ));
            }
        }
        
        log.info("Bulk attestation completed for app {}: {} successful, {} failed", 
                appId, successful.size(), failed.size());
        
        return BulkAttestationResponse.create(successful, failed);
    }

    @Override
    @Transactional
    @Audited(action = "USER_ATTEST_EVIDENCE_FIELD", subjectType = "profile_field", subject = "#request.profileFieldId()",
             context = {"appId=#appId", "attestedBy=#request.attestedBy()", "attestationType=#request.attestationType()"})
    public IndividualAttestationResponse processIndividualAttestation(String appId, IndividualAttestationRequest request) {
        log.debug("Processing individual attestation for app {} field {} by {} (type: {})", 
                 appId, request.profileFieldId(), request.attestedBy(), request.attestationType());

        try {
            // 1. Validate request
            if (!request.isValid()) {
                return IndividualAttestationResponse.failed(request.profileFieldId(), 
                    "Invalid request: profileFieldId, attestedBy, and attestationType are required");
            }

            if (!request.isValidAttestationType()) {
                return IndividualAttestationResponse.failed(request.profileFieldId(), 
                    "Invalid attestationType. Must be one of: compliance, exception, remediation");
            }

            // 2. Find evidence to attest
            String evidenceId = request.evidenceId();
            
            if (evidenceId == null || evidenceId.isBlank()) {
                // Find pending evidence for this profile field
                List<EvidenceFieldLink> pendingLinks = evidenceFieldLinkRepository
                    .findByProfileFieldIdAndStatus(request.profileFieldId(), EvidenceFieldLinkStatus.PENDING_PO_REVIEW);
                
                if (pendingLinks.isEmpty()) {
                    return IndividualAttestationResponse.failed(request.profileFieldId(), 
                        "No evidence found with PENDING_PO_REVIEW status for this field");
                }
                
                evidenceId = pendingLinks.get(0).getEvidenceId();
            }

            // 3. Validate evidence exists
            if (!evidenceRepository.findEvidenceById(evidenceId).isPresent()) {
                return IndividualAttestationResponse.failed(request.profileFieldId(), 
                    "Evidence not found with ID: " + evidenceId);
            }

            // 4. Process the attestation using external auditable service
            String commentsToUse = request.attestationComments() != null ? 
                request.attestationComments() : "Individual attestation - " + request.attestationType();

            EvidenceFieldLinkResponse linkResponse = evidenceAttestationService.attestEvidenceFieldLink(
                evidenceId, request.profileFieldId(), request.attestedBy(), commentsToUse, "individual"
            );

            // 5. Generate attestation ID
            String attestationId = generateAttestationId(evidenceId, request.profileFieldId());

            log.info("Successfully processed individual attestation for field {} by {} with ID {}", 
                    request.profileFieldId(), request.attestedBy(), attestationId);

            return IndividualAttestationResponse.success(attestationId, request.profileFieldId(), 
                linkResponse.reviewedAt());

        } catch (NotFoundException e) {
            log.warn("Evidence field link not found for individual attestation: {}", e.getMessage());
            return IndividualAttestationResponse.failed(request.profileFieldId(), 
                "Profile field not found or evidence link missing");
        } catch (Exception e) {
            log.error("Error processing individual attestation for field {}: {}", 
                     request.profileFieldId(), e.getMessage(), e);
            return IndividualAttestationResponse.failed(request.profileFieldId(), 
                "Failed to process attestation: " + e.getMessage());
        }
    }
}