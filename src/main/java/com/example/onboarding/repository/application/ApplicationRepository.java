package com.example.onboarding.repository.application;

import com.example.onboarding.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApplicationRepository extends JpaRepository<Application, String>, JpaSpecificationExecutor<Application> {}
