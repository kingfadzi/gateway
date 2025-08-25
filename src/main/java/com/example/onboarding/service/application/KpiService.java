package com.example.onboarding.service.application;

import com.example.onboarding.dto.application.PortfolioKpis;
import com.example.onboarding.repository.application.KpiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class KpiService {

    private final KpiRepository repo;

    public KpiService(KpiRepository repo) {
        this.repo = repo;
    }

    public PortfolioKpis getPortfolioKpis() {
        final int compliant       = repo.compliant();
        final int missingEvidence = repo.missingEvidence();
        final int pendingReview   = repo.pendingReview();
        final int riskBlocked     = repo.riskBlocked();
        return new PortfolioKpis(compliant, missingEvidence, pendingReview, riskBlocked);
    }

    public PortfolioKpis getApplicationKpis(String appId) {
        final int compliant       = repo.compliantForApp(appId);
        final int missingEvidence = repo.missingEvidenceForApp(appId);
        final int pendingReview   = repo.pendingReviewForApp(appId);
        final int riskBlocked     = repo.riskBlockedForApp(appId);
        return new PortfolioKpis(compliant, missingEvidence, pendingReview, riskBlocked);
    }
}
