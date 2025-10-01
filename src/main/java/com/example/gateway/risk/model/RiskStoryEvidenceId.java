package com.example.gateway.risk.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class RiskStoryEvidenceId implements Serializable {

    private String riskId;
    private String evidenceId;
}
