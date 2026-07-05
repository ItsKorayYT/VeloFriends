package com.velofriends.velocityfriends.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.model.PlayerSettings;
import com.velofriends.velocityfriends.model.ServerVisibility;
import com.velofriends.velocityfriends.storage.SocialRepository;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class NotificationService {
    private final ProxyServer proxy;
    private final SocialRepository social;
    private final MessageManager messages;
    private final PluginConfig config;
    private final Executor executor;

    public NotificationService(ProxyServer proxy, SocialRepository social, MessageManager messages, PluginConfig config, Executor executor) {
        this.proxy = proxy;
        this.social = social;
        this.messages = messages;
        this.config = config;
        this.executor = executor;
    }

    public void joined(Player player) {
        if (!config.notifications().friendJoin()) {
            return;
        }
        notifyFriends(player, config.messageFormats().join(), Map.of("player", player.getUsername()));
    }

    public void left(Player player) {
        if (!config.notifications().friendLeave()) {
            return;
        }
        notifyFriends(player, config.messageFormats().leave(), Map.of("player", player.getUsername()));
    }

    public void changedServer(Player player, String serverName) {
        if (!config.notifications().friendServerChange()) {
            return;
        }
        notifyFriends(player, config.messageFormats().serverChange(), Map.of("player", player.getUsername(), "server", serverName));
    }

    private void notifyFriends(Player subject, String format, Map<String, String> placeholders) {
        CompletableFuture.runAsync(() -> {
            for (UUID friendId : social.friends(subject.getUniqueId()).join()) {
                Optional<Player> viewer = proxy.getPlayer(friendId);
                if (viewer.isEmpty()) {
                    continue;
                }
                PlayerSettings viewerSettings = social.settings(friendId).join();
                if (!viewerSettings.friendNotifications()) {
                    continue;
                }
                if (!canSeeOnline(viewer.get(), subject.getUniqueId())) {
                    continue;
                }
                if (placeholders.containsKey("server") && !canSeeServer(viewer.get(), subject.getUniqueId())) {
                    continue;
                }
                Component component = messages.fromFormat(format, placeholders);
                viewer.get().sendMessage(component);
            }
        }, executor);
    }

    private boolean canSeeOnline(Player viewer, UUID subject) {
        PlayerSettings settings = social.settings(subject).join();
        return switch (settings.onlineStatusVisibility()) {
            case EVERYONE -> true;
            case FRIENDS_ONLY -> social.areFriends(viewer.getUniqueId(), subject).join();
            case NOBODY -> viewer.hasPermission("velocityfriends.bypass.privacy");
        };
    }

    private boolean canSeeServer(Player viewer, UUID subject) {
        PlayerSettings settings = social.settings(subject).join();
        return settings.serverVisibility() == ServerVisibility.FRIENDS
                || viewer.hasPermission("velocityfriends.bypass.privacy");
    }
}
