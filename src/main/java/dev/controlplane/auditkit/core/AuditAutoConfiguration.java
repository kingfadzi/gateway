package dev.controlplane.auditkit.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@AutoConfiguration
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuditSinkClient auditSinkClient(RestTemplate restTemplate, AuditProperties props) {
        return new AuditSinkClient(restTemplate, props);
    }

    @Bean
    @ConditionalOnProperty(prefix = "audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuditAspect auditAspect(AuditProperties props, AuditSinkClient client) {
        return new AuditAspect(props, client);
    }
}
