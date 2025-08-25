package com.example.onboarding.controller.application;


import com.example.onboarding.dto.application.PortfolioKpis;
import com.example.onboarding.service.application.KpiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/apps")
public class KpiController {

    private final KpiService service;

    public KpiController(KpiService service) {
        this.service = service;
    }

    @GetMapping("/kpis")
    public PortfolioKpis portfolioKpis() {

        return service.getPortfolioKpis();
    }

    @GetMapping("/{appId}/kpis")
    public PortfolioKpis applicationKpis(@PathVariable String appId) {
        return service.getApplicationKpis(appId);
    }
}
