package com.example.onboarding.repository.risk;

import com.example.onboarding.model.risk.RiskStory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskStoryRepository extends JpaRepository<RiskStory, String> {
}
