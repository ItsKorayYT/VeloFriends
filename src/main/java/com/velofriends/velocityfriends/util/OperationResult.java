package com.velofriends.velocityfriends.util;

import java.util.LinkedHashMap;
import java.util.Map;

public record OperationResult(boolean success, String messageKey, Map<String, String> placeholders) {
    public static OperationResult success(String key) {
        return new OperationResult(true, key, Map.of());
    }

    public static OperationResult success(String key, Map<String, String> placeholders) {
        return new OperationResult(true, key, placeholders);
    }

    public static OperationResult failure(String key) {
        return new OperationResult(false, key, Map.of());
    }

    public static OperationResult failure(String key, Map<String, String> placeholders) {
        return new OperationResult(false, key, placeholders);
    }

    public static Map<String, String> placeholders(String... entries) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put(entries[i], entries[i + 1]);
        }
        return map;
    }
}
