package com.example.onboarding.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceFieldLinkId implements Serializable {
    
    private String evidenceId;
    private String profileFieldId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvidenceFieldLinkId that = (EvidenceFieldLinkId) o;
        return Objects.equals(evidenceId, that.evidenceId) && 
               Objects.equals(profileFieldId, that.profileFieldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(evidenceId, profileFieldId);
    }
}