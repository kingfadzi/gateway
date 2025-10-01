package com.example.onboarding.risk.repository;

import com.example.onboarding.risk.model.RiskStoryEvidence;
import com.example.onboarding.risk.model.RiskStoryEvidenceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskStoryEvidenceRepository extends JpaRepository<RiskStoryEvidence, RiskStoryEvidenceId> {
}
