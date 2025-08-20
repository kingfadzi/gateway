package com.example.onboarding.application.service;

import java.util.Map;
import java.util.Set;

public final class RatingsNormalizer {
    private RatingsNormalizer(){}

    private static final Set<String> AB_CD = Set.of("A","B","C","D");
    private static final Set<String> A1A2BCD = Set.of("A1","A2","B","C","D");
    private static final Set<String> RES = Set.of("0","1","2","3","4");

    private static String upper(String s) { return s == null ? null : s.trim().toUpperCase(); }

    public static String letter(String v, String deflt) {
        String u = upper(v);
        return (u != null && AB_CD.contains(u)) ? u : deflt;
    }

    public static String security(String v) {
        String u = upper(v);
        if ("A".equals(u)) u = "A1";
        return (u != null && A1A2BCD.contains(u)) ? u : "A2";
    }

    public static String resilience(String v, String deflt) {
        String u = v == null ? null : v.trim();
        return (u != null && RES.contains(u)) ? u : deflt;
    }

    /** Canonical normalized context (exact keys your ETL writes) */
    public static Map<String,Object> normalizeCtx(String appCriticality, String sec, String integ, String avail, String resil) {
        return Map.of(
                "app_criticality",  letter(appCriticality, "C"),
                "security_rating",  security(sec),
                "integrity_rating", letter(integ, "C"),
                "availability_rating", letter(avail, "C"),
                "resilience_rating", resilience(resil, "2")
        );
    }
}
