package com.velofriends.velocityfriends.service;

import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.model.PlayerSettings;
import com.velofriends.velocityfriends.model.PrivacyLevel;
import com.velofriends.velocityfriends.model.ServerVisibility;
import com.velofriends.velocityfriends.storage.SocialRepository;
import com.velofriends.velocityfriends.util.OperationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class SettingsService {
    private final SocialRepository social;
    private final NameResolver names;
    private final Executor executor;

    public SettingsService(SocialRepository social, NameResolver names, Executor executor) {
        this.social = social;
        this.names = names;
        this.executor = executor;
    }

    public CompletableFuture<PlayerSettings> settings(Player player) {
        return social.settings(player.getUniqueId());
    }

    public CompletableFuture<OperationResult> setDmPrivacy(Player player, PrivacyLevel privacy) {
        return update(player, current -> current.withDmPrivacy(privacy), "DM privacy", privacy.configValue());
    }

    public CompletableFuture<OperationResult> setOnlinePrivacy(Player player, PrivacyLevel privacy) {
        return update(player, current -> current.withOnlineStatusVisibility(privacy), "online visibility", privacy.configValue());
    }

    public CompletableFuture<OperationResult> setServerVisibility(Player player, ServerVisibility visibility) {
        return update(player, current -> current.withServerVisibility(visibility), "server visibility", visibility.configValue());
    }

    public CompletableFuture<BlockedIgnoredView> blockedIgnored(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> blocked = new ArrayList<>();
                for (UUID uuid : social.blocks(player.getUniqueId()).join()) {
                    blocked.add(names.name(uuid).join());
                }
                List<String> ignored = new ArrayList<>();
                for (UUID uuid : social.ignores(player.getUniqueId()).join()) {
                    ignored.add(names.name(uuid).join());
                }
                return new BlockedIgnoredView(blocked, ignored);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private CompletableFuture<OperationResult> update(Player player, SettingsUpdater updater, String settingName, String value) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerSettings settings = updater.update(social.settings(player.getUniqueId()).join());
            social.saveSettings(settings).join();
            return OperationResult.success("privacy-updated", Map.of("setting", settingName, "value", value));
        }, executor);
    }

    @FunctionalInterface
    private interface SettingsUpdater {
        PlayerSettings update(PlayerSettings current);
    }

    public record BlockedIgnoredView(List<String> blocked, List<String> ignored) {
    }
}
