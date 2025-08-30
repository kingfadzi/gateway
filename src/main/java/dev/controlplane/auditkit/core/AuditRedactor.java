package dev.controlplane.auditkit.core;

import dev.controlplane.auditkit.annotations.AuditRedact;

import java.lang.reflect.RecordComponent;
import java.util.*;

public class AuditRedactor {
    private final List<String> redactKeys;
    private final int maxBytes;

    public AuditRedactor(List<String> redactKeys, int maxBytes) {
        this.redactKeys = redactKeys;
        this.maxBytes = maxBytes;
    }

    public Map<String,Object> snapshot(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map<?,?> m) {
            Map<String,Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : m.entrySet()) copy.put(String.valueOf(e.getKey()), e.getValue());
            maskRecursive(copy);
            return copy;
        }
        Map<String,Object> out = new LinkedHashMap<>();
        Class<?> c = obj.getClass();
        if (c.isRecord()) {
            for (RecordComponent rc : c.getRecordComponents()) {
                try {
                    Object v = rc.getAccessor().invoke(obj);
                    AuditRedact ar = rc.getAnnotation(AuditRedact.class);
                    String name = rc.getName();
                    if (ar != null) {
                        switch (ar.strategy()) {
                            case OMIT -> { continue; }
                            case MASK -> v = "***";
                            case HASH -> v = HashUtil.sha256Base64(String.valueOf(v));
                        }
                    } else if (shouldRedactKey(name)) {
                        v = "***";
                    }
                    out.put(name, v);
                } catch (Exception ignored) {}
            }
        } else {
            out.put("value", String.valueOf(obj));
        }
        maskRecursive(out);
        // hard cap by dropping details and keeping only size
        int size = out.toString().getBytes().length;
        if (size > maxBytes) {
            return Map.of("truncated", true, "sizeBytes", size);
        }
        return out;
    }

    private boolean shouldRedactKey(String k) {
        for (String rk : redactKeys) if (rk.equalsIgnoreCase(k)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private void maskRecursive(Object node) {
        if (node instanceof Map<?,?>) {
            Map<Object,Object> map = (Map<Object,Object>) node;
            for (Map.Entry<Object,Object> e : map.entrySet()) {
                if (e.getKey() != null && shouldRedactKey(String.valueOf(e.getKey()))) {
                    e.setValue("***");
                } else {
                    maskRecursive(e.getValue());
                }
            }
        } else if (node instanceof Iterable<?> it) {
            for (Object child : it) maskRecursive(child);
        }
    }
}
