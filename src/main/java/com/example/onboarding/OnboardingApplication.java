package com.example.onboarding;

import com.example.onboarding.config.AutoProfileProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AutoProfileProperties.class)
public class OnboardingApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnboardingApplication.class, args);
    }
}