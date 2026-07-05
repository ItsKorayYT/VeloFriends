package com.velofriends.velocityfriends.model;

import java.util.UUID;

public record FriendView(
        UUID uuid,
        String username,
        boolean online,
        String serverName,
        boolean favorite,
        String note
) {
}
