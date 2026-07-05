package com.velofriends.velocityfriends.model;

public enum ServerVisibility {
    FRIENDS,
    NOBODY;

    public static ServerVisibility fromConfig(String value, ServerVisibility fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase()) {
            case "friends", "friends_only", "friends-only", "friendsonly" -> FRIENDS;
            case "nobody", "none", "off" -> NOBODY;
            default -> fallback;
        };
    }

    public String configValue() {
        return switch (this) {
            case FRIENDS -> "friends";
            case NOBODY -> "nobody";
        };
    }
}
