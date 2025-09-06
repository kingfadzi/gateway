package dev.controlplane.auditkit.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "audit")
public class AuditProperties {
    private boolean enabled = true;
    private String producerId = "unknown-producer";
    private Sink sink = new Sink();
    private String apiKey;
    private boolean includeArgsDefault = true;
    private boolean includeResultDefault = false;
    private boolean logOnFailure = true;
    private int maxJsonBytes = 4096;
    private List<String> redactKeys = List.of("password", "token", "secret", "attachment", "content", "data");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProducerId() { return producerId; }
    public void setProducerId(String producerId) { this.producerId = producerId; }

    public Sink getSink() { return sink; }
    public void setSink(Sink sink) { this.sink = sink; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public boolean isIncludeArgsDefault() { return includeArgsDefault; }
    public void setIncludeArgsDefault(boolean includeArgsDefault) { this.includeArgsDefault = includeArgsDefault; }

    public boolean isIncludeResultDefault() { return includeResultDefault; }
    public void setIncludeResultDefault(boolean includeResultDefault) { this.includeResultDefault = includeResultDefault; }

    public int getMaxJsonBytes() { return maxJsonBytes; }
    public void setMaxJsonBytes(int maxJsonBytes) { this.maxJsonBytes = maxJsonBytes; }

    public List<String> getRedactKeys() { return redactKeys; }
    public void setRedactKeys(List<String> redactKeys) { this.redactKeys = redactKeys; }

    public boolean isLogOnFailure() { return logOnFailure; }
    public void setLogOnFailure(boolean logOnFailure) { this.logOnFailure = logOnFailure; }

    public static class Sink {
        private String url;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
