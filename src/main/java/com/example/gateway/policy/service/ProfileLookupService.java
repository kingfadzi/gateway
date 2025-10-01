package com.example.gateway.policy.service;

import com.example.gateway.policy.repository.ProfileFieldLookupRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProfileLookupService {

    private final ProfileFieldLookupRepository repo;

    public ProfileLookupService(ProfileFieldLookupRepository repo) {
        this.repo = repo;
    }

    /** Return the profile_field.id for latest app profile + fieldKey, if present. */
    public Optional<String> resolveFieldId(String appId, String fieldKey) {
        return repo.resolveProfileFieldIdForAppAndKey(appId, fieldKey);
    }

    /** List available field keys for the app’s latest profile (helps callers discover valid keys). */
    public List<String> listFieldKeys(String appId) {
        return repo.listFieldKeysForApp(appId);
    }
}
