package com.example.onboarding.service.risk;

import com.example.onboarding.dto.risk.AttachEvidenceRequest;
import com.example.onboarding.dto.risk.CreateRiskStoryRequest;
import com.example.onboarding.dto.risk.RiskStoryResponse;
import com.example.onboarding.dto.risk.RiskStoryEvidenceResponse;
import com.example.onboarding.dto.track.Track;
import com.example.onboarding.exception.DataIntegrityException;
import com.example.onboarding.exception.NotFoundException;
import com.example.onboarding.model.RiskStatus;
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
