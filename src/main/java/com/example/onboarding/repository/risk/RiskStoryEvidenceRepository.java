package com.example.onboarding.repository.risk;

import com.example.onboarding.model.risk.RiskStoryEvidence;
import com.example.onboarding.model.risk.RiskStoryEvidenceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskStoryEvidenceRepository extends JpaRepository<RiskStoryEvidence, RiskStoryEvidenceId> {
}
