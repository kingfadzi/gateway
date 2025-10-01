package com.example.gateway.risk.repository;

import com.example.gateway.risk.model.RiskStoryEvidence;
import com.example.gateway.risk.model.RiskStoryEvidenceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskStoryEvidenceRepository extends JpaRepository<RiskStoryEvidence, RiskStoryEvidenceId> {
}
