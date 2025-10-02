package com.example.gateway.risk.service;

import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.risk.dto.AttachEvidenceRequest;
import com.example.gateway.risk.dto.CreateRiskStoryRequest;
import com.example.gateway.risk.dto.RiskStoryResponse;
import com.example.gateway.risk.dto.RiskStoryEvidenceResponse;
import com.example.gateway.risk.mapper.RiskStoryRowMapper;
import com.example.gateway.track.dto.Track;
import com.example.gateway.application.dto.exception.DataIntegrityException;
import com.example.gateway.application.dto.exception.NotFoundException;
import com.example.gateway.risk.model.RiskStatus;
import com.example.gateway.risk.model.RiskStory;
import com.example.gateway.risk.model.RiskStoryEvidence;
import com.example.gateway.risk.model.RiskStoryEvidenceId;
import com.example.gateway.risk.repository.RiskStoryRepository;
import com.example.gateway.risk.repository.RiskStoryEvidenceRepository;
import com.example.gateway.evidence.service.EvidenceService;
import com.example.gateway.track.service.TrackService;
import com.example.gateway.registry.service.ComplianceContextService;
import dev.controlplane.auditkit.annotations.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RiskStoryServiceImpl implements RiskStoryService {

    private static final Logger log = LoggerFactory.getLogger(RiskStoryServiceImpl.class);

    private final RiskStoryRepository riskStoryRepository;
    private final RiskStoryEvidenceRepository riskStoryEvidenceRepository;
    private final TrackService trackService;
    private final EvidenceService evidenceService;
    private final ComplianceContextService complianceContextService;
    private final RiskAutoCreationService riskAutoCreationService;
    private final RiskStoryRowMapper rowMapper;

    public RiskStoryServiceImpl(RiskStoryRepository riskStoryRepository,
                                RiskStoryEvidenceRepository riskStoryEvidenceRepository,
                                TrackService trackService,
                                EvidenceService evidenceService,
                                ComplianceContextService complianceContextService,
                                RiskAutoCreationService riskAutoCreationService,
                                RiskStoryRowMapper rowMapper) {
        this.riskStoryRepository = riskStoryRepository;
        this.riskStoryEvidenceRepository = riskStoryEvidenceRepository;
        this.trackService = trackService;
        this.evidenceService = evidenceService;
        this.complianceContextService = complianceContextService;
        this.riskAutoCreationService = riskAutoCreationService;
        this.rowMapper = rowMapper;
    }

    @Override
    @Transactional
    @Audited(action = "CREATE_RISK_STORY", subjectType = "risk_story", subject = "#result.riskId",
             context = {"appId=#appId", "fieldKey=#fieldKey", "creationType=#request.creationType", "assignedSme=#result.assignedSme"})
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
        String appRating = riskAutoCreationService.getAppRatingForField(appId, fieldKey);
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
    public PageResponse<RiskStoryResponse> getRisksByAppIdPaginated(String appId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        long total = riskStoryRepository.countByAppId(appId);
        List<Map<String, Object>> risks = riskStoryRepository.findRisksByAppIdWithPagination(appId, safePageSize, offset);
        List<RiskStoryResponse> riskResponses = risks.stream().map(rowMapper::mapToRiskStoryResponse).collect(Collectors.toList());

        return new PageResponse<>(safePage, safePageSize, total, riskResponses);
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

        String finalDerivedFrom = RiskStoryRowMapper.convertDomainToDerivedFrom(domain, derivedFrom);
        int offset = page * size;

        List<Map<String, Object>> results = riskStoryRepository.searchRisks(
            appId, assignedSme, status, finalDerivedFrom, fieldKey,
            severity, creationType, triggeringEvidenceId, sortBy, sortOrder, size, offset);

        return results.stream()
                .map(rowMapper::mapToRiskStoryResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public PageResponse<RiskStoryResponse> searchRisksWithPagination(String appId, String assignedSme, String status,
                                                                   String domain, String derivedFrom, String fieldKey,
                                                                   String severity, String creationType, String triggeringEvidenceId,
                                                                   String sortBy, String sortOrder, int page, int size) {

        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        String finalDerivedFrom = RiskStoryRowMapper.convertDomainToDerivedFrom(domain, derivedFrom);

        // Get total count for pagination
        long total = riskStoryRepository.countSearchResults(
            appId, assignedSme, status, finalDerivedFrom, fieldKey,
            severity, creationType, triggeringEvidenceId);

        // Get paginated results
        List<Map<String, Object>> results = riskStoryRepository.searchRisks(
            appId, assignedSme, status, finalDerivedFrom, fieldKey,
            severity, creationType, triggeringEvidenceId, sortBy, sortOrder, safeSize, offset);

        List<RiskStoryResponse> riskResponses = results.stream()
                .map(rowMapper::mapToRiskStoryResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(safePage, safeSize, total, riskResponses);
    }
}
