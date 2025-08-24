package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.evidence.ReviewEvidenceRequest;
import com.example.onboarding.dto.evidence.ReviewEvidenceResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface EvidenceService {
    Evidence createOrDedup(String appId, CreateEvidenceRequest req, MultipartFile file) throws Exception;
    Evidence get(String evidenceId);
    ReviewEvidenceResponse review(String evidenceId, ReviewEvidenceRequest req);
    List<Evidence> listByAppAndField(String appId, String fieldKey);
}