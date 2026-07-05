package com.velofriends.velocityfriends.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.service.NotificationService;
import com.velofriends.velocityfriends.storage.PlayerRepository;
import org.slf4j.Logger;

public final class PlayerListener {
    private final PlayerRepository players;
    private final NotificationService notifications;
    private final Logger logger;

    public PlayerListener(PlayerRepository players, NotificationService notifications, Logger logger) {
        this.players = players;
        this.notifications = notifications;
        this.logger = logger;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        players.upsert(player.getUniqueId(), player.getUsername()).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                logger.warn("Unable to cache player {}", player.getUsername(), throwable);
                return;
            }
            notifications.joined(player);
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        notifications.left(player);
        players.touchLastSeen(player.getUniqueId()).exceptionally(throwable -> {
            logger.warn("Unable to update last seen for {}", player.getUsername(), throwable);
            return null;
        });
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        notifications.changedServer(event.getPlayer(), event.getServer().getServerInfo().getName());
    }
}
