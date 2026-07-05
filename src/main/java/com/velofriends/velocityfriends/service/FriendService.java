package com.velofriends.velocityfriends.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.model.FriendRequest;
import com.velofriends.velocityfriends.model.FriendView;
import com.velofriends.velocityfriends.model.PlayerSettings;
import com.velofriends.velocityfriends.model.ResolvedPlayer;
import com.velofriends.velocityfriends.model.ServerVisibility;
import com.velofriends.velocityfriends.storage.PlayerRepository;
import com.velofriends.velocityfriends.storage.SocialRepository;
import com.velofriends.velocityfriends.util.DurationFormatter;
import com.velofriends.velocityfriends.util.OperationResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class FriendService {
    private final ProxyServer proxy;
    private final PlayerRepository players;
    private final SocialRepository social;
    private final NameResolver names;
    private final PermissionLimitResolver limits;
    private final MessageManager messages;
    private final PluginConfig config;
    private final Executor executor;
    private final Map<UUID, Long> lastRequestAt = new ConcurrentHashMap<>();

    public FriendService(ProxyServer proxy, PlayerRepository players, SocialRepository social, NameResolver names,
                         PermissionLimitResolver limits, MessageManager messages, PluginConfig config, Executor executor) {
        this.proxy = proxy;
        this.players = players;
        this.social = social;
        this.names = names;
        this.limits = limits;
        this.messages = messages;
        this.config = config;
        this.executor = executor;
    }

    public CompletableFuture<OperationResult> add(Player sender, String targetName) {
        return operation(() -> {
            ResolvedPlayer target = resolveOrThrow(targetName);
            UUID senderId = sender.getUniqueId();
            if (senderId.equals(target.uuid())) {
                return OperationResult.failure("cannot-self");
            }
            if (social.areFriends(senderId, target.uuid()).join()) {
                return OperationResult.failure("already-friends", ph("target", target.username()));
            }
            if (social.isBlocked(target.uuid(), senderId).join() || social.isBlocked(senderId, target.uuid()).join()) {
                return OperationResult.failure("dm-blocked", ph("target", target.username()));
            }
            PlayerSettings targetSettings = social.settings(target.uuid()).join();
            if (!targetSettings.friendRequestsEnabled() && !sender.hasPermission("velocityfriends.bypass.privacy")) {
                return OperationResult.failure("friend-requests-target-disabled", ph("target", target.username()));
            }
            Optional<FriendRequest> reciprocal = social.request(target.uuid(), senderId).join();
            if (reciprocal.isPresent()) {
                FriendRequest request = reciprocal.get();
                if (request.expired()) {
                    social.deleteRequest(target.uuid(), senderId).join();
                    return OperationResult.failure("friend-request-expired");
                }
                return acceptResolved(sender, target);
            }
            if (social.request(senderId, target.uuid()).join().isPresent()) {
                return OperationResult.failure("friend-request-existing", ph("target", target.username()));
            }
            long now = Instant.now().toEpochMilli();
            long cooldownMillis = Duration.ofSeconds(config.friends().requestCooldownSeconds()).toMillis();
            Long previous = lastRequestAt.get(senderId);
            if (previous != null && now - previous < cooldownMillis && !sender.hasPermission("velocityfriends.bypass.cooldown")) {
                return OperationResult.failure("cooldown", ph("time", DurationFormatter.compact(Duration.ofMillis(cooldownMillis - (now - previous)))));
            }
            int senderLimit = limits.limit(sender);
            if (social.friendCount(senderId).join() >= senderLimit) {
                return OperationResult.failure("friend-limit-reached", ph("count", String.valueOf(senderLimit)));
            }
            Optional<Player> targetOnline = target.onlinePlayer();
            int targetLimit = targetOnline.map(limits::limit).orElse(config.friends().defaultLimit());
            if (social.friendCount(target.uuid()).join() >= targetLimit) {
                return OperationResult.failure("friend-limit-target-reached", ph("target", target.username()));
            }
            long expiresAt = now + Duration.ofMinutes(config.friends().requestExpiryMinutes()).toMillis();
            social.createRequest(senderId, target.uuid(), expiresAt).join();
            lastRequestAt.put(senderId, now);
            target.onlinePlayer().ifPresent(player -> messages.send(player, "friend-request-received", ph("player", sender.getUsername())));
            return OperationResult.success("friend-request-sent", ph("target", target.username()));
        });
    }

    public CompletableFuture<OperationResult> accept(Player sender, String requesterName) {
        return operation(() -> acceptResolved(sender, resolveOrThrow(requesterName)));
    }

    public CompletableFuture<OperationResult> deny(Player sender, String requesterName) {
        return operation(() -> {
            ResolvedPlayer requester = resolveOrThrow(requesterName);
            Optional<FriendRequest> request = social.request(requester.uuid(), sender.getUniqueId()).join();
            if (request.isEmpty()) {
                return OperationResult.failure("friend-request-none", ph("target", requester.username()));
            }
            social.deleteRequest(requester.uuid(), sender.getUniqueId()).join();
            requester.onlinePlayer().ifPresent(player -> messages.send(player, "friend-request-denied-target", ph("player", sender.getUsername())));
            return OperationResult.success("friend-request-denied", ph("target", requester.username()));
        });
    }



    public CompletableFuture<OperationResult> cancel(Player sender, String targetName) {
        return operation(() -> {
            ResolvedPlayer target = resolveOrThrow(targetName);
            Optional<FriendRequest> request = social.request(sender.getUniqueId(), target.uuid()).join();
            if (request.isEmpty()) {
                return OperationResult.failure("friend-request-none", ph("target", target.username()));
            }
            social.deleteRequest(sender.getUniqueId(), target.uuid()).join();
            return OperationResult.success("friend-request-denied", ph("target", target.username()));
        });
    }

    public CompletableFuture<OperationResult> remove(Player sender, String targetName) {
        return operation(() -> {
            ResolvedPlayer target = resolveOrThrow(targetName);
            if (!social.areFriends(sender.getUniqueId(), target.uuid()).join()) {
                return OperationResult.failure("not-friends", ph("target", target.username()));
            }
            social.removeFriendship(sender.getUniqueId(), target.uuid()).join();
            target.onlinePlayer().ifPresent(player -> messages.send(player, "friend-removed-target", ph("player", sender.getUsername())));
            return OperationResult.success("friend-removed", ph("target", target.username()));
        });
    }

    public CompletableFuture<OperationResult> block(Player sender, String targetName) {
        return operation(() -> {
            ResolvedPlayer target = resolveOrThrow(targetName);
            if (sender.getUniqueId().equals(target.uuid())) {
                return OperationResult.failure("cannot-self");
            }
            if (social.isBlocked(sender.getUniqueId(), target.uuid()).join()) {
                return OperationResult.failure("already-blocked", ph("target", target.username()));
            }
            social.block(sender.getUniqueId(), target.uuid()).join();
            return OperationResult.success("blocked", ph("target", target.username()));
        });
    }

    public CompletableFuture<OperationResult> unblock(Player sender, String targetName) {
        return operation(() -> {
            ResolvedPlayer target = resolveOrThrow(targetName);
            if (!social.isBlocked(sender.getUniqueId(), target.uuid()).join()) {
                return OperationResult.failure("not-blocked", ph("target", target.username()));
            }
            social.unblock(sender.getUniqueId(), target.uuid()).join();
            return OperationResult.success("unblocked", ph("target", target.username()));
        });
    }

    public CompletableFuture<OperationResult> toggleRequests(Player sender) {
        return operation(() -> {
            PlayerSettings settings = social.settings(sender.getUniqueId()).join();
            PlayerSettings updated = settings.withFriendRequestsEnabled(!settings.friendRequestsEnabled());
            social.saveSettings(updated).join();
            return OperationResult.success(updated.friendRequestsEnabled() ? "friend-requests-enabled" : "friend-requests-disabled");
        });
    }

    public CompletableFuture<OperationResult> favorite(Player sender, String targetName) {
        return operation(() -> {
            ResolvedPlayer target = resolveOrThrow(targetName);
            if (!social.areFriends(sender.getUniqueId(), target.uuid()).join()) {
                return OperationResult.failure("not-friends", ph("target", target.username()));
            }
            boolean favorite = social.toggleFavorite(sender.getUniqueId(), target.uuid()).join();
            return OperationResult.success(favorite ? "favorite-on" : "favorite-off", ph("target", target.username()));
        });
    }

    public CompletableFuture<OperationResult> note(Player sender, String targetName, String note) {
        return operation(() -> {
            ResolvedPlayer target = resolveOrThrow(targetName);
            if (!social.areFriends(sender.getUniqueId(), target.uuid()).join()) {
                return OperationResult.failure("not-friends", ph("target", target.username()));
            }
            social.setNote(sender.getUniqueId(), target.uuid(), note.strip()).join();
            return OperationResult.success("note-set", ph("target", target.username()));
        });
    }

    public CompletableFuture<List<FriendView>> friends(Player viewer, boolean favoritesOnly) {
        return value(() -> {
            List<UUID> friendIds = favoritesOnly ? social.favorites(viewer.getUniqueId()).join() : social.friends(viewer.getUniqueId()).join();
            Set<UUID> favorites = new HashSet<>(social.favorites(viewer.getUniqueId()).join());
            List<FriendView> result = new ArrayList<>();
            for (UUID friendId : friendIds) {
                String name = names.name(friendId).join();
                Optional<Player> online = proxy.getPlayer(friendId);
                boolean onlineVisible = online.isPresent() && canSeeOnline(viewer, friendId);
                String server = onlineVisible && canSeeServer(viewer, friendId)
                        ? online.flatMap(Player::getCurrentServer).map(ServerConnection::getServerInfo).map(info -> info.getName()).orElse("-")
                        : "-";
                String note = social.note(viewer.getUniqueId(), friendId).join().orElse("");
                result.add(new FriendView(friendId, name, onlineVisible, server, favorites.contains(friendId), note));
            }
            result.sort(Comparator.comparing(FriendView::favorite).reversed().thenComparing(FriendView::username, String.CASE_INSENSITIVE_ORDER));
            return result;
        });
    }

    public CompletableFuture<RequestsView> requests(Player viewer) {
        return value(() -> {
            List<RequestLine> incoming = new ArrayList<>();
            for (FriendRequest request : social.incomingRequests(viewer.getUniqueId()).join()) {
                incoming.add(new RequestLine(names.name(request.requesterUuid()).join(), request.timeRemaining(), request.expired()));
            }
            List<RequestLine> outgoing = new ArrayList<>();
            for (FriendRequest request : social.outgoingRequests(viewer.getUniqueId()).join()) {
                outgoing.add(new RequestLine(names.name(request.targetUuid()).join(), request.timeRemaining(), request.expired()));
            }
            return new RequestsView(incoming, outgoing);
        });
    }

    public CompletableFuture<OperationResult> forceAdd(String firstName, String secondName) {
        return operation(() -> {
            ResolvedPlayer first = resolveOrThrow(firstName);
            ResolvedPlayer second = resolveOrThrow(secondName);
            if (first.uuid().equals(second.uuid())) {
                return OperationResult.failure("cannot-self");
            }
            social.addFriendship(first.uuid(), second.uuid()).join();
            return OperationResult.success("admin-forceadd", ph("player", first.username(), "target", second.username()));
        });
    }

    public CompletableFuture<OperationResult> forceRemove(String firstName, String secondName) {
        return operation(() -> {
            ResolvedPlayer first = resolveOrThrow(firstName);
            ResolvedPlayer second = resolveOrThrow(secondName);
            social.removeFriendship(first.uuid(), second.uuid()).join();
            return OperationResult.success("admin-forceremove", ph("player", first.username(), "target", second.username()));
        });
    }

    public CompletableFuture<OperationResult> purgeOld(int days) {
        return operation(() -> {
            long cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, days))).toEpochMilli();
            int purged = social.purgePlayersOlderThan(cutoff, config.friends().purgeBatchSize()).join().purged();
            return OperationResult.success("purge-complete", ph("count", String.valueOf(purged)));
        });
    }

    public CompletableFuture<SocialRepository.DebugSnapshot> debug(String targetName) {
        return value(() -> social.debug(resolveOrThrow(targetName).uuid()).join());
    }

    private OperationResult acceptResolved(Player sender, ResolvedPlayer requester) {
        Optional<FriendRequest> request = social.request(requester.uuid(), sender.getUniqueId()).join();
        if (request.isEmpty()) {
            return OperationResult.failure("friend-request-none", ph("target", requester.username()));
        }
        if (request.get().expired()) {
            social.deleteRequest(requester.uuid(), sender.getUniqueId()).join();
            return OperationResult.failure("friend-request-expired");
        }
        social.addFriendship(sender.getUniqueId(), requester.uuid()).join();
        requester.onlinePlayer().ifPresent(player -> messages.send(player, "friend-added-target", ph("player", sender.getUsername())));
        return OperationResult.success("friend-added", ph("target", requester.username()));
    }

    private boolean canSeeOnline(Player viewer, UUID target) {
        PlayerSettings settings = social.settings(target).join();
        return switch (settings.onlineStatusVisibility()) {
            case EVERYONE -> true;
            case FRIENDS_ONLY -> social.areFriends(viewer.getUniqueId(), target).join();
            case NOBODY -> viewer.getUniqueId().equals(target) || viewer.hasPermission("velocityfriends.bypass.privacy");
        };
    }

    private boolean canSeeServer(Player viewer, UUID target) {
        PlayerSettings settings = social.settings(target).join();
        if (settings.serverVisibility() == ServerVisibility.NOBODY && !viewer.hasPermission("velocityfriends.bypass.privacy")) {
            return viewer.getUniqueId().equals(target);
        }
        return social.areFriends(viewer.getUniqueId(), target).join() || viewer.hasPermission("velocityfriends.bypass.privacy");
    }

    private ResolvedPlayer resolveOrThrow(String name) throws UnknownPlayerException {
        return names.resolve(name).join().orElseThrow(() -> new UnknownPlayerException(name));
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

    private <T> CompletableFuture<T> value(ServiceSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
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

    public record RequestLine(String playerName, Duration remaining, boolean expired) {
    }

    public record RequestsView(List<RequestLine> incoming, List<RequestLine> outgoing) {
    }
}
