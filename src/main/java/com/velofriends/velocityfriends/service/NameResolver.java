package com.velofriends.velocityfriends.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velofriends.velocityfriends.model.PlayerProfile;
import com.velofriends.velocityfriends.model.ResolvedPlayer;
import com.velofriends.velocityfriends.storage.PlayerRepository;
import com.velofriends.velocityfriends.util.PlayerNames;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NameResolver {
    private final ProxyServer proxy;
    private final PlayerRepository players;

    public NameResolver(ProxyServer proxy, PlayerRepository players) {
        this.proxy = proxy;
        this.players = players;
    }

    public CompletableFuture<Optional<ResolvedPlayer>> resolve(String name) {
        if (!PlayerNames.valid(name)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<Player> online = proxy.getPlayer(name);
        if (online.isPresent()) {
            Player player = online.get();
            return players.upsert(player.getUniqueId(), player.getUsername())
                    .thenApply(ignored -> Optional.of(new ResolvedPlayer(player.getUniqueId(), player.getUsername(), Optional.of(player))));
        }
        return players.findByName(name).thenApply(profile -> profile.map(this::offline));
    }

    public CompletableFuture<String> name(UUID uuid) {
        Optional<Player> online = proxy.getPlayer(uuid);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online.get().getUsername());
        }
        return players.findByUuid(uuid).thenApply(profile -> profile.map(PlayerProfile::username).orElse(uuid.toString()));
    }

    private ResolvedPlayer offline(PlayerProfile profile) {
        return new ResolvedPlayer(profile.uuid(), profile.username(), Optional.empty());
    }
}
