package com.example.gateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Jsons {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Jsons(){}

    public static String toJson(Object o) {
        try { return MAPPER.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException(e); }
    }
}
