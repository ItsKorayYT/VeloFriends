package com.velofriends.velocityfriends.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record FriendRequest(UUID requesterUuid, UUID targetUuid, long createdAt, long expiresAt) {
    public boolean expired() {
        return expiresAt <= Instant.now().toEpochMilli();
    }

    public Duration timeRemaining() {
        return Duration.ofMillis(Math.max(0L, expiresAt - Instant.now().toEpochMilli()));
    }
}
