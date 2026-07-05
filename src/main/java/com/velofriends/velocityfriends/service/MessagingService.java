package com.velofriends.velocityfriends.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.model.PlayerSettings;
import com.velofriends.velocityfriends.model.ResolvedPlayer;
import com.velofriends.velocityfriends.storage.SocialRepository;
import com.velofriends.velocityfriends.util.OperationResult;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class MessagingService {
    private final ProxyServer proxy;
    private final SocialRepository social;
    private final NameResolver names;
    private final MessageManager messages;
    private final PluginConfig config;
    private final Executor executor;
    private final Map<UUID, UUID> lastMessaged = new ConcurrentHashMap<>();

    public MessagingService(ProxyServer proxy, SocialRepository social, NameResolver names,
                            MessageManager messages, PluginConfig config, Executor executor) {
        this.proxy = proxy;
        this.social = social;
        this.names = names;
        this.messages = messages;
        this.config = config;
        this.executor = executor;
    }

    public CompletableFuture<OperationResult> send(Player sender, String targetName, String body) {
        return operation(() -> {
            ResolvedPlayer resolved = names.resolve(targetName).join().orElseThrow(() -> new UnknownPlayerException(targetName));
            if (sender.getUniqueId().equals(resolved.uuid())) {
                return OperationResult.failure("dm-self");
            }
            Optional<Player> optionalTarget = resolved.onlinePlayer().or(() -> proxy.getPlayer(resolved.uuid()));
            if (optionalTarget.isEmpty()) {
                return OperationResult.failure("player-not-found", ph("target", resolved.username()));
            }
            Player target = optionalTarget.get();
            PlayerSettings senderSettings = social.settings(sender.getUniqueId()).join();
            PlayerSettings targetSettings = social.settings(target.getUniqueId()).join();
            if (!senderSettings.messagesEnabled()) {
                return OperationResult.failure("dm-disabled");
            }
            if (!targetSettings.messagesEnabled()) {
                return OperationResult.failure("dm-target-disabled", ph("target", target.getUsername()));
            }
            if (social.isIgnored(target.getUniqueId(), sender.getUniqueId()).join()) {
                return OperationResult.failure("dm-ignored", ph("target", target.getUsername()));
            }
            if (social.isBlocked(sender.getUniqueId(), target.getUniqueId()).join()
                    || social.isBlocked(target.getUniqueId(), sender.getUniqueId()).join()) {
                return OperationResult.failure("dm-blocked", ph("target", target.getUsername()));
            }
            if (!allowedByPrivacy(sender, target, targetSettings)) {
                return OperationResult.failure("dm-privacy-denied", ph("target", target.getUsername()));
            }
            String cleanBody = body.strip();
            Map<String, String> placeholders = ph("player", sender.getUsername(), "target", target.getUsername(), "message", cleanBody);
            Component incoming = messages.fromFormat(config.messageFormats().incomingDm(), placeholders);
            Component outgoing = messages.fromFormat(config.messageFormats().outgoingDm(), placeholders);
            target.sendMessage(incoming);
            sender.sendMessage(outgoing);
            if (targetSettings.actionbarDm()) {
                target.sendActionBar(incoming);
            }
            lastMessaged.put(sender.getUniqueId(), target.getUniqueId());
            lastMessaged.put(target.getUniqueId(), sender.getUniqueId());
            sendSpy(sender, target, cleanBody);
            return OperationResult.success("", Map.of());
        });
    }

    public CompletableFuture<OperationResult> reply(Player sender, String body) {
        UUID target = lastMessaged.get(sender.getUniqueId());
        if (target == null) {
            return CompletableFuture.completedFuture(OperationResult.failure("no-reply-target"));
        }
        Optional<Player> online = proxy.getPlayer(target);
        if (online.isEmpty()) {
            lastMessaged.remove(sender.getUniqueId());
            return CompletableFuture.completedFuture(OperationResult.failure("player-not-found", ph("target", target.toString())));
        }
        return send(sender, online.get().getUsername(), body);
    }

    public CompletableFuture<OperationResult> toggleMessages(Player player) {
        return operation(() -> {
            PlayerSettings settings = social.settings(player.getUniqueId()).join();
            PlayerSettings updated = settings.withMessagesEnabled(!settings.messagesEnabled());
            social.saveSettings(updated).join();
            return OperationResult.success(updated.messagesEnabled() ? "dm-enabled" : "dm-disabled");
        });
    }

    public CompletableFuture<OperationResult> toggleSocialSpy(Player player) {
        return operation(() -> {
            PlayerSettings settings = social.settings(player.getUniqueId()).join();
            PlayerSettings updated = settings.withSocialSpy(!settings.socialSpy());
            social.saveSettings(updated).join();
            return OperationResult.success(updated.socialSpy() ? "socialspy-on" : "socialspy-off");
        });
    }

    public CompletableFuture<OperationResult> ignore(Player player, String targetName) {
        return operation(() -> {
            ResolvedPlayer target = names.resolve(targetName).join().orElseThrow(() -> new UnknownPlayerException(targetName));
            if (player.getUniqueId().equals(target.uuid())) {
                return OperationResult.failure("cannot-self");
            }
            if (social.isIgnored(player.getUniqueId(), target.uuid()).join()) {
                return OperationResult.failure("already-ignored", ph("target", target.username()));
            }
            social.ignore(player.getUniqueId(), target.uuid()).join();
            return OperationResult.success("ignored", ph("target", target.username()));
        });
    }

    public CompletableFuture<OperationResult> unignore(Player player, String targetName) {
        return operation(() -> {
            ResolvedPlayer target = names.resolve(targetName).join().orElseThrow(() -> new UnknownPlayerException(targetName));
            if (!social.isIgnored(player.getUniqueId(), target.uuid()).join()) {
                return OperationResult.failure("not-ignored", ph("target", target.username()));
            }
            social.unignore(player.getUniqueId(), target.uuid()).join();
            return OperationResult.success("unignored", ph("target", target.username()));
        });
    }

    private boolean allowedByPrivacy(Player sender, Player target, PlayerSettings targetSettings) {
        if (sender.hasPermission("velocityfriends.bypass.privacy")) {
            return true;
        }
        return switch (targetSettings.dmPrivacy()) {
            case EVERYONE -> true;
            case FRIENDS_ONLY -> social.areFriends(sender.getUniqueId(), target.getUniqueId()).join();
            case NOBODY -> false;
        };
    }

    private void sendSpy(Player sender, Player target, String body) {
        Map<String, String> placeholders = ph("player", sender.getUsername(), "target", target.getUsername(), "message", body);
        Component spy = messages.fromFormat(config.messageFormats().spyDm(), placeholders);
        for (Player online : proxy.getAllPlayers()) {
            if (online.getUniqueId().equals(sender.getUniqueId()) || online.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            boolean enabled = online.hasPermission("velocityfriends.admin.spy")
                    || (online.hasPermission("velocityfriends.command.socialspy") && social.settings(online.getUniqueId()).join().socialSpy());
            if (enabled) {
                online.sendMessage(spy);
            }
        }
    }

    private CompletableFuture<OperationResult> operation(ServiceSupplier<OperationResult> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (UnknownPlayerException exception) {
                return OperationResult.failure("player-not-found", ph("target", exception.name()));
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private static Map<String, String> ph(String... entries) {
        return OperationResult.placeholders(entries);
    }

    @FunctionalInterface
    private interface ServiceSupplier<T> {
        T get() throws Exception;
    }

    private static final class UnknownPlayerException extends Exception {
        private final String name;

        private UnknownPlayerException(String name) {
            this.name = name;
        }

        private String name() {
            return name;
        }
    }
}
