package com.velofriends.velocityfriends.service;

import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.PluginConfig;

import java.util.Comparator;

public final class PermissionLimitResolver {
    private final PluginConfig.Friends config;

    public PermissionLimitResolver(PluginConfig.Friends config) {
        this.config = config;
    }

    public int limit(Player player) {
        return config.permissionLimits().entrySet().stream()
                .filter(entry -> player.hasPermission(entry.getKey()))
                .map(entry -> entry.getValue())
                .max(Comparator.naturalOrder())
                .orElse(config.defaultLimit());
    }
}
