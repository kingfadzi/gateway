package com.example.onboarding.service.risk;

import com.example.onboarding.dto.risk.AttachEvidenceRequest;
import com.example.onboarding.dto.risk.CreateRiskStoryRequest;
import com.example.onboarding.dto.risk.RiskStoryResponse;
import com.example.onboarding.dto.risk.RiskStoryEvidenceResponse;
import com.example.onboarding.dto.track.Track;
import com.example.onboarding.exception.DataIntegrityException;
import com.example.onboarding.exception.NotFoundException;
import com.example.onboarding.model.RiskStatus;
import com.example.onboarding.model.RiskCreationType;
import com.example.onboarding.model.risk.RiskStory;
import com.example.onboarding.model.risk.RiskStoryEvidence;
import com.example.onboarding.model.risk.RiskStoryEvidenceId;
import com.example.onboarding.repository.risk.RiskStoryRepository;
import com.example.onboarding.repository.risk.RiskStoryEvidenceRepository;
import com.example.onboarding.repository.application.ApplicationManagementRepository;
import com.example.onboarding.service.evidence.EvidenceService;
import com.example.onboarding.service.track.TrackService;
import com.example.onboarding.service.ComplianceContextService;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;
import com.example.onboarding.dto.profile.ProfileFieldTypeInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RiskStoryServiceImpl implements RiskStoryService {

    private final RiskStoryRepository riskStoryRepository;
    private final RiskStoryEvidenceRepository riskStoryEvidenceRepository;
    private final TrackService trackService;
    private final EvidenceService evidenceService;
    private final ComplianceContextService complianceContextService;
    private final ApplicationManagementRepository applicationRepository;
    private final ProfileFieldRegistryService profileFieldRegistryService;

    public RiskStoryServiceImpl(RiskStoryRepository riskStoryRepository,
                                RiskStoryEvidenceRepository riskStoryEvidenceRepository,
                                TrackService trackService,
                                EvidenceService evidenceService,
                                ComplianceContextService complianceContextService,
                                ApplicationManagementRepository applicationRepository,
                                ProfileFieldRegistryService profileFieldRegistryService) {
        this.riskStoryRepository = riskStoryRepository;
        this.riskStoryEvidenceRepository = riskStoryEvidenceRepository;
        this.trackService = trackService;
        this.evidenceService = evidenceService;
        this.complianceContextService = complianceContextService;
        this.applicationRepository = applicationRepository;
        this.profileFieldRegistryService = profileFieldRegistryService;
    }

    @Override
    @Transactional
    public RiskStoryResponse createRiskStory(String appId, String fieldKey, CreateRiskStoryRequest request) {
        if (request.trackId() != null) {
            Track track = trackService.getTrackById(request.trackId())
                    .orElseThrow(() -> new NotFoundException("Track not found with id: " + request.trackId()));
            if (!track.appId().equals(appId)) {
                throw new DataIntegrityException("RiskStoryService", appId, "trackId", request.trackId(), "Track does not belong to the application.");
            }
        }

        RiskStory riskStory = new RiskStory();
        riskStory.setRiskId("risk_" + UUID.randomUUID());
        riskStory.setAppId(appId);
        riskStory.setFieldKey(fieldKey);
        riskStory.setTitle(request.title());
        riskStory.setHypothesis(request.hypothesis());
        riskStory.setCondition(request.condition());
        riskStory.setConsequence(request.consequence());
        riskStory.setControlRefs(request.controlRefs());
        riskStory.setAttributes(request.attributes());
        riskStory.setSeverity(request.severity() != null ? request.severity() : "medium");
        riskStory.setStatus(request.status() != null ? RiskStatus.valueOf(request.status().toUpperCase()) : RiskStatus.PENDING_SME_REVIEW);
        riskStory.setRaisedBy(request.raisedBy());
        riskStory.setOwner(request.owner());
        riskStory.setTrackId(request.trackId());
        riskStory.setOpenedAt(OffsetDateTime.now());
        
        // Get app rating for compliance snapshot
        String appRating = getAppRatingForField(appId, fieldKey);
        riskStory.setPolicyRequirementSnapshot(complianceContextService.getComplianceSnapshot(fieldKey, appRating));

        RiskStory savedRiskStory = riskStoryRepository.save(riskStory);
        return RiskStoryResponse.fromModel(savedRiskStory);
    }

    @Override
    @Transactional
    public RiskStoryEvidenceResponse attachEvidence(String riskId, AttachEvidenceRequest request) {
        riskStoryRepository.findById(riskId)
                .orElseThrow(() -> new NotFoundException("Risk story not found with id: " + riskId));

        evidenceService.getEvidenceById(request.evidenceId())
                .orElseThrow(() -> new NotFoundException("Evidence not found with id: " + request.evidenceId()));

        if (riskStoryEvidenceRepository.existsById(new RiskStoryEvidenceId(riskId, request.evidenceId()))) {
            throw new DataIntegrityException("RiskStoryService", riskId, "evidenceId", request.evidenceId(), "Evidence already attached to this risk story.");
        }

        RiskStoryEvidence riskStoryEvidence = new RiskStoryEvidence();
        riskStoryEvidence.setRiskId(riskId);
        riskStoryEvidence.setEvidenceId(request.evidenceId());
        riskStoryEvidence.setSubmittedBy(request.submittedBy());

        RiskStoryEvidence savedRiskStoryEvidence = riskStoryEvidenceRepository.save(riskStoryEvidence);
        return RiskStoryEvidenceResponse.fromModel(savedRiskStoryEvidence);
    }

    @Override
    @Transactional
    public void detachEvidence(String riskId, String evidenceId) {
        RiskStoryEvidenceId id = new RiskStoryEvidenceId(riskId, evidenceId);
        if (!riskStoryEvidenceRepository.existsById(id)) {
            throw new NotFoundException("Evidence not attached to this risk story.");
        }
        riskStoryEvidenceRepository.deleteById(id);
    }

    @Override
    public List<RiskStoryResponse> getRisksByAppId(String appId) {
        List<RiskStory> risks = riskStoryRepository.findByAppId(appId);
        return risks.stream()
                .map(RiskStoryResponse::fromModel)
                .collect(Collectors.toList());
    }
    
    @Override
    public RiskStoryResponse getRiskById(String riskId) {
        RiskStory risk = riskStoryRepository.findById(riskId)
                .orElseThrow(() -> new NotFoundException("Risk story not found: " + riskId));
        return RiskStoryResponse.fromModel(risk);
    }
    
    @Override
    public List<RiskStoryResponse> getRisksByAppIdAndFieldKey(String appId, String fieldKey) {
        List<RiskStory> risks = riskStoryRepository.findByAppIdAndFieldKey(appId, fieldKey);
        return risks.stream()
                .map(RiskStoryResponse::fromModel)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<RiskStoryResponse> getRisksByProfileFieldId(String profileFieldId) {
        List<RiskStory> risks = riskStoryRepository.findByProfileFieldId(profileFieldId);
        return risks.stream()
                .map(RiskStoryResponse::fromModel)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<RiskStoryResponse> searchRisks(String appId, String assignedSme, String status, 
                                             String domain, String derivedFrom, String fieldKey, 
                                             String severity, String creationType, String triggeringEvidenceId,
                                             String sortBy, String sortOrder, int page, int size) {
        
        // Convert domain to derivedFrom if domain is provided but derivedFrom is not
        String finalDerivedFrom = derivedFrom;
        if (domain != null && derivedFrom == null) {
            finalDerivedFrom = domain + "_rating";
        }
        
        int offset = page * size;
        
        List<Map<String, Object>> results = riskStoryRepository.searchRisks(
            appId, assignedSme, status, finalDerivedFrom, fieldKey, 
            severity, creationType, triggeringEvidenceId, sortBy, sortOrder, size, offset);
        
        return results.stream()
                .map(this::mapToRiskStoryResponse)
                .collect(Collectors.toList());
    }
    
    private RiskStoryResponse mapToRiskStoryResponse(Map<String, Object> row) {
        // Create a RiskStory from the row data (without app details)
        RiskStory riskStory = mapToRiskStory(row);
        
        // Extract all application details
        RiskStoryResponse.ApplicationDetails applicationDetails = mapToApplicationDetails(row);
        
        return RiskStoryResponse.fromModel(riskStory, applicationDetails);
    }
    
    private RiskStoryResponse.ApplicationDetails mapToApplicationDetails(Map<String, Object> row) {
        return new RiskStoryResponse.ApplicationDetails(
            (String) row.get("name"),
            (String) row.get("scope"),
            (String) row.get("parent_app_id"),
            (String) row.get("parent_app_name"),
            (String) row.get("business_service_name"),
            (String) row.get("app_criticality_assessment"),
            (String) row.get("security_rating"),
            (String) row.get("confidentiality_rating"),
            (String) row.get("integrity_rating"),
            (String) row.get("availability_rating"),
            (String) row.get("resilience_rating"),
            (String) row.get("business_application_sys_id"),
            (String) row.get("architecture_hosting"),
            (String) row.get("jira_backlog_id"),
            (String) row.get("lean_control_service_id"),
            (String) row.get("repo_id"),
            (String) row.get("operational_status"),
            (String) row.get("transaction_cycle"),
            (String) row.get("transaction_cycle_id"),
            (String) row.get("application_type"),
            (String) row.get("application_tier"),
            (String) row.get("architecture_type"),
            (String) row.get("install_type"),
            (String) row.get("house_position"),
            (String) row.get("product_owner"),
            (String) row.get("product_owner_brid"),
            (String) row.get("system_architect"),
            (String) row.get("system_architect_brid"),
            (String) row.get("onboarding_status"),
            (String) row.get("owner_id"),
            convertToOffsetDateTime(row.get("app_created_at")),
            convertToOffsetDateTime(row.get("app_updated_at"))
        );
    }
    
    private RiskStory mapToRiskStory(Map<String, Object> row) {
        RiskStory risk = new RiskStory();
        risk.setRiskId((String) row.get("risk_id"));
        risk.setAppId((String) row.get("app_id"));
        risk.setFieldKey((String) row.get("field_key"));
        risk.setProfileId((String) row.get("profile_id"));
        risk.setProfileFieldId((String) row.get("profile_field_id"));
        risk.setTrackId((String) row.get("track_id"));
        risk.setTriggeringEvidenceId((String) row.get("triggering_evidence_id"));
        risk.setCreationType(row.get("creation_type") != null ? 
            RiskCreationType.valueOf((String) row.get("creation_type")) : null);
        risk.setAssignedSme((String) row.get("assigned_sme"));
        risk.setTitle((String) row.get("title"));
        risk.setHypothesis((String) row.get("hypothesis"));
        risk.setCondition((String) row.get("condition"));
        risk.setConsequence((String) row.get("consequence"));
        risk.setControlRefs((String) row.get("control_refs"));
        risk.setSeverity((String) row.get("severity"));
        risk.setStatus(row.get("status") != null ? 
            RiskStatus.valueOf((String) row.get("status")) : null);
        risk.setClosureReason((String) row.get("closure_reason"));
        risk.setRaisedBy((String) row.get("raised_by"));
        risk.setOwner((String) row.get("owner"));
        risk.setReviewComment((String) row.get("review_comment"));
        
        // Handle JSONB fields - these come as PGobject or similar from PostgreSQL
        // For now, we'll set them to empty maps if they're complex, or parse them if needed
        risk.setAttributes(new java.util.HashMap<>());
        risk.setPolicyRequirementSnapshot(new java.util.HashMap<>());
        
        // Handle timestamps (they can come as various types from the database)
        risk.setOpenedAt(convertToOffsetDateTime(row.get("opened_at")));
        risk.setClosedAt(convertToOffsetDateTime(row.get("closed_at")));
        risk.setAssignedAt(convertToOffsetDateTime(row.get("assigned_at")));
        risk.setReviewedAt(convertToOffsetDateTime(row.get("reviewed_at")));
        risk.setCreatedAt(convertToOffsetDateTime(row.get("created_at")));
        risk.setUpdatedAt(convertToOffsetDateTime(row.get("updated_at")));
        
        return risk;
    }
    
    private OffsetDateTime convertToOffsetDateTime(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        
        if (timestamp instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) timestamp).toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        
        if (timestamp instanceof java.time.Instant) {
            return ((java.time.Instant) timestamp).atOffset(java.time.ZoneOffset.UTC);
        }
        
        if (timestamp instanceof java.time.OffsetDateTime) {
            return (OffsetDateTime) timestamp;
        }
        
        if (timestamp instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) timestamp).atOffset(java.time.ZoneOffset.UTC);
        }
        
        // If we can't convert, log and return null
        System.err.println("Unable to convert timestamp type: " + timestamp.getClass().getName() + " value: " + timestamp);
        return null;
    }
    
    /**
     * Get app rating for a specific field (mirrors logic from RiskAutoCreationServiceImpl)
     */
    private String getAppRatingForField(String appId, String fieldKey) {
        try {
            Optional<ProfileFieldTypeInfo> fieldInfo = profileFieldRegistryService.getFieldTypeInfo(fieldKey);
            if (fieldInfo.isEmpty()) {
                return "C1"; // Default fallback
            }
            
            String derivedFrom = fieldInfo.get().derivedFrom();
            String rating = applicationRepository.getApplicationRatingByColumn(appId, derivedFrom);
            return rating == null ? "C1" : rating;
        } catch (Exception e) {
            return "C1"; // Default fallback on any error
        }
    }
}
