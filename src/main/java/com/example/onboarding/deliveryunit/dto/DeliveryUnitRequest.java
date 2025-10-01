package com.example.onboarding.deliveryunit.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveryUnitRequest {
    private String appId;
    private ArtifactLinks artifacts;
    private List<Contact> contacts;
    // getters and setters
}