package com.example.gateway.application.repository;

import com.example.gateway.application.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApplicationRepository extends JpaRepository<Application, String>, JpaSpecificationExecutor<Application> {}
