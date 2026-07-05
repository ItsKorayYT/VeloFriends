package com.velofriends.velocityfriends.model;

import com.velocitypowered.api.proxy.Player;

import java.util.Optional;
import java.util.UUID;

public record ResolvedPlayer(UUID uuid, String username, Optional<Player> onlinePlayer) {
    public boolean online() {
        return onlinePlayer.isPresent();
    }
}
