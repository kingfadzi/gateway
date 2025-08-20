package com.example.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "autoprofile")
public class AutoProfileProperties {
    /** Enable auto-profile on POST /api/apps */
    private boolean enabled = true;

    /** Registry YAML file: classpath:profile-fields.registry.yaml or file:/path.yaml */
    private String registryPath = "classpath:profile-fields.registry.yaml";

    /** Profile scope_type */
    private String scopeType = "application";

    /** Profile version integer */
    private int profileVersion = 1;

    /** Source fetch timeout ms (if you add timeouts later) */
    private int fetchTimeoutMs = 3000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRegistryPath() { return registryPath; }
    public void setRegistryPath(String registryPath) { this.registryPath = registryPath; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public int getProfileVersion() { return profileVersion; }
    public void setProfileVersion(int profileVersion) { this.profileVersion = profileVersion; }

    public int getFetchTimeoutMs() { return fetchTimeoutMs; }
    public void setFetchTimeoutMs(int fetchTimeoutMs) { this.fetchTimeoutMs = fetchTimeoutMs; }
}
