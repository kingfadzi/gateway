// com.example.onboarding.config.FeaturesProperties
package com.example.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "features")
public class FeaturesProperties {
    private boolean reuseSuggestions = true; // default on in dev/staging
    public boolean isReuseSuggestions() { return reuseSuggestions; }
    public void setReuseSuggestions(boolean reuseSuggestions) { this.reuseSuggestions = reuseSuggestions; }
}
