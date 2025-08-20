package com.example.onboarding.service.policy;

import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import org.springframework.web.multipart.MultipartFile;

public interface EvidenceService {
    EvidenceDto createOrDedup(String appId, CreateEvidenceRequest req, MultipartFile file) throws Exception;
    EvidenceDto get(String evidenceId);
}
