package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import org.springframework.web.multipart.MultipartFile;

public interface EvidenceService {
    EvidenceDto createOrDedup(String appId, CreateEvidenceRequest req, MultipartFile file) throws Exception;
    EvidenceDto get(String evidenceId);
}