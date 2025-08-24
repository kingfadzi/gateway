package com.example.onboarding.service.profile;

import com.example.onboarding.config.AutoProfileProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RegistryDeriver {

    public record Item(String key, String derivedFrom, Map<String, Object> rule) {}

    private final ResourceLoader resourceLoader;
    private final AutoProfileProperties props;
    private final AtomicReference<List<Item>> registry = new AtomicReference<>(List.of());

    public RegistryDeriver(ResourceLoader resourceLoader, AutoProfileProperties props) {
        this.resourceLoader = resourceLoader;
        this.props = props;
    }

    @PostConstruct
    public void load() {
        try {
            Resource res = resourceLoader.getResource(props.getRegistryPath());
            try (InputStream in = res.getInputStream()) {
                Map<String, Object> y = new Yaml().load(in);
                List<Map<String, Object>> fields = (List<Map<String,Object>>) y.getOrDefault("fields", List.of());
                List<Item> items = new ArrayList<>();
                for (Map<String,Object> m : fields) {
                    String key = Objects.toString(m.get("key"), null);
                    String df  = Objects.toString(m.get("derived_from"), null);
                    Map<String,Object> rule = (Map<String,Object>) m.get("rule");
                    if (key != null && df != null && rule != null) {
                        items.add(new Item(key, df, rule));
                    }
                }
                registry.set(List.copyOf(items));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load registry: " + props.getRegistryPath(), e);
        }
    }

    /** Compute derived profile fields from normalized ctx */
    public Map<String,Object> derive(Map<String,Object> ctx) {
        Map<String,Object> out = new LinkedHashMap<>();
        for (Item it : registry.get()) {
            Object v = ctx.get(it.derivedFrom());
            if (v == null) continue;
            Object mapped = it.rule().get(String.valueOf(v));
            if (mapped != null) out.put(it.key(), mapped);
        }
        return out;
    }
}
