package com.velofriends.velocityfriends.model;

import java.util.Locale;
import java.util.UUID;

public record PlayerProfile(UUID uuid, String username, long firstSeen, long lastSeen) {
    public String usernameLower() {
        return username.toLowerCase(Locale.ROOT);
    }
}
