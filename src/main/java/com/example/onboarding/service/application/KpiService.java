package com.example.onboarding.service.application;

import com.example.onboarding.dto.application.PortfolioKpis;
import com.example.onboarding.dto.application.KpiSummary;
import com.example.onboarding.model.Application;
import com.example.onboarding.repository.application.KpiRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

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
    
    public KpiSummary calculateKpisFromApplications(List<Application> applications) {
        if (applications.isEmpty()) {
            return new KpiSummary(0, 0, 0, 0);
        }
        
        int compliant = 0;
        int missingEvidence = 0;
        int pendingReview = 0;
        int riskBlocked = 0;
        
        for (Application app : applications) {
            compliant += repo.compliantForApp(app.getAppId());
            missingEvidence += repo.missingEvidenceForApp(app.getAppId());
            pendingReview += repo.pendingReviewForApp(app.getAppId());
            riskBlocked += repo.riskBlockedForApp(app.getAppId());
        }
        
        return new KpiSummary(compliant, missingEvidence, pendingReview, riskBlocked);
    }
}
