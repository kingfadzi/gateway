package com.example.onboarding.service;

import com.example.onboarding.model.ApplicationMetadata;
import com.example.onboarding.model.AppMetadataResponse;
import com.example.onboarding.repository.ApplicationMetadataRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationMetadataServiceTest {

    @Test
    void getFullAppMetadata_shouldReturnAppWithChildren() {
        String appId = "APP123042";
        ApplicationMetadata parent = new ApplicationMetadata();
        parent.setAppId(appId);
        parent.setAppName("Jumpstart App");

        ApplicationMetadata child = new ApplicationMetadata();
        child.setAppId("APP321001");
        child.setAppName("Auth Service");

        parent.setChildren(List.of(child));

        ApplicationMetadataRepository repo = mock(ApplicationMetadataRepository.class);
        when(repo.findByAppId(appId)).thenReturn(Optional.of(parent));

        ApplicationMetadataService service = new ApplicationMetadataService(repo);
        AppMetadataResponse response = service.getFullAppMetadata(appId);

        assertNotNull(response);
        assertEquals(appId, response.getAppId());
        assertEquals(1, response.getApplicationComponents().size());
        assertEquals("Auth Service", response.getApplicationComponents().get(0).getName());
    }

    @Test
    void getFullAppMetadata_shouldReturnNullIfNotFound() {
        ApplicationMetadataRepository repo = mock(ApplicationMetadataRepository.class);
        when(repo.findByAppId("DOES_NOT_EXIST")).thenReturn(Optional.empty());

        ApplicationMetadataService service = new ApplicationMetadataService(repo);
        AppMetadataResponse response = service.getFullAppMetadata("DOES_NOT_EXIST");

        assertNull(response);
    }
}
