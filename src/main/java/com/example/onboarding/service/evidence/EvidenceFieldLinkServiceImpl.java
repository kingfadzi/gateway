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
import com.example.onboarding.service.risk.RiskAutoCreationService;
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

    public EvidenceFieldLinkServiceImpl(EvidenceFieldLinkRepository evidenceFieldLinkRepository,
                                       RiskAutoCreationService riskAutoCreationService,
                                       EvidenceRepository evidenceRepository) {
        this.evidenceFieldLinkRepository = evidenceFieldLinkRepository;
        this.riskAutoCreationService = riskAutoCreationService;
        this.evidenceRepository = evidenceRepository;
    }

    @Override
    @Transactional
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

        // Create the link
        EvidenceFieldLink link = new EvidenceFieldLink();
        link.setEvidenceId(evidenceId);
        link.setProfileFieldId(profileFieldId);
        link.setAppId(appId);
        link.setLinkStatus(EvidenceFieldLinkStatus.PENDING_REVIEW);
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
}