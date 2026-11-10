package com.mowoo.mowoo_backend.ai.solar;

import java.util.List;
import java.util.Map;

public final class SolarSchemas {
    private SolarSchemas() { }
    public static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties,
                "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }
}
