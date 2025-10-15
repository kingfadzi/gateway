package com.example.gateway.application.service;

import com.example.gateway.application.dto.AppSummaryResponse;
import com.example.gateway.application.dto.RiskMetrics;
import com.example.gateway.application.model.Application;
import com.example.gateway.application.repository.ApplicationRepository;
import com.example.gateway.application.repository.ApplicationSpecifications;
import com.example.gateway.risk.service.RiskMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApplicationQueryService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationQueryService.class);

    private final ApplicationRepository repository;
    private final RiskMetricsService riskMetricsService;

    public ApplicationQueryService(
            ApplicationRepository repository,
            RiskMetricsService riskMetricsService) {
        this.repository = repository;
        this.riskMetricsService = riskMetricsService;
    }

    public List<Application> search(Map<String, String> params) {
        Map<String, Object> filteredParams = params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Specification<Application> spec = ApplicationSpecifications.withDynamicParams(filteredParams);
        return repository.findAll(spec);
    }

    public Application getById(String appId) {
        return repository.findById(appId).orElse(null);
    }
    
    public List<AppSummaryResponse> convertToSummaryResponse(List<Application> applications) {
        return convertToSummaryResponse(applications, false);
    }

    /**
     * Convert applications to summary responses with optional risk metrics.
     *
     * @param applications List of applications to convert
     * @param includeRiskMetrics If true, fetches and includes risk metrics (uses batch query)
     * @return List of AppSummaryResponse with optional risk metrics
     */
    public List<AppSummaryResponse> convertToSummaryResponse(
            List<Application> applications,
            boolean includeRiskMetrics) {

        if (!includeRiskMetrics) {
            // Fast path: no risk metrics needed
            return applications.stream()
                    .map(this::toAppSummaryResponse)
                    .collect(Collectors.toList());
        }

        // Fetch risk metrics for all apps in a single batch query
        List<String> appIds = applications.stream()
                .map(Application::getAppId)
                .collect(Collectors.toList());

        log.debug("Fetching risk metrics for {} applications", appIds.size());
        Map<String, RiskMetrics> metricsMap = riskMetricsService.calculateForApps(appIds);

        // Map applications to responses with risk metrics
        return applications.stream()
                .map(app -> toAppSummaryResponseWithMetrics(app, metricsMap.get(app.getAppId())))
                .collect(Collectors.toList());
    }

    private AppSummaryResponse toAppSummaryResponse(Application app) {
        return new AppSummaryResponse(
            app.getAppId(),
            app.getName(),
            app.getAppCriticalityAssessment(),
            app.getBusinessServiceName(),
            app.getApplicationType(),
            app.getInstallType(),
            app.getArchitectureType()
        );
    }

    private AppSummaryResponse toAppSummaryResponseWithMetrics(Application app, RiskMetrics metrics) {
        return new AppSummaryResponse(
            app.getAppId(),
            app.getName(),
            app.getAppCriticalityAssessment(),
            app.getBusinessServiceName(),
            app.getApplicationType(),
            app.getInstallType(),
            app.getArchitectureType(),
            metrics != null ? metrics : RiskMetrics.empty()
        );
    }
}
