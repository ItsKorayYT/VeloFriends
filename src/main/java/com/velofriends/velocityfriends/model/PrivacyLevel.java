package com.velofriends.velocityfriends.model;

public enum PrivacyLevel {
    EVERYONE,
    FRIENDS_ONLY,
    NOBODY;

    public static PrivacyLevel fromConfig(String value, PrivacyLevel fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase()) {
            case "everyone", "all" -> EVERYONE;
            case "friends", "friends_only", "friends-only", "friendsonly" -> FRIENDS_ONLY;
            case "nobody", "none", "off" -> NOBODY;
            default -> fallback;
        };
    }

    public String configValue() {
        return switch (this) {
            case EVERYONE -> "everyone";
            case FRIENDS_ONLY -> "friends";
            case NOBODY -> "nobody";
        };
    }
}
