package com.example.gateway.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashIds {
    private HashIds() {}

    public static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String profileId(String scopeType, String scopeId, int version) {
        return "prof_" + md5Hex(scopeType + ":" + scopeId + ":" + version);
    }

    public static String profileFieldId(String profileId, String key) {
        return "pf_" + md5Hex(profileId + ":" + key);
    }
}
