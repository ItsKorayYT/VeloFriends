package com.velofriends.velocityfriends.menu;

import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.model.FriendView;
import com.velofriends.velocityfriends.model.PlayerSettings;
import com.velofriends.velocityfriends.model.PrivacyLevel;
import com.velofriends.velocityfriends.model.ServerVisibility;
import com.velofriends.velocityfriends.service.FriendService;
import com.velofriends.velocityfriends.service.SettingsService;
import com.velofriends.velocityfriends.util.DurationFormatter;
import com.velofriends.velocityfriends.util.Page;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Map;

public final class JavaMenuRenderer {
    private final FriendService friends;
    private final SettingsService settings;
    private final MessageManager messages;
    private final PluginConfig config;

    public JavaMenuRenderer(FriendService friends, SettingsService settings, MessageManager messages, PluginConfig config) {
        this.friends = friends;
        this.settings = settings;
        this.messages = messages;
        this.config = config;
    }

    public void main(Player player) {
        header(player, config.gui().titles().main());
        player.sendMessage(link(config.gui().buttons().friends(), "/friendsgui friends", "Open friends"));
        player.sendMessage(link(config.gui().buttons().requests(), "/friendsgui requests", "Open requests"));
        player.sendMessage(link(config.gui().buttons().directMessage(), "/friendsgui dm", "Message an online player"));
        player.sendMessage(link(config.gui().buttons().privacy(), "/friendsgui privacy", "Open privacy settings"));
        player.sendMessage(link(config.gui().buttons().blocked(), "/friendsgui blocked", "Open blocked and ignored players"));
        player.sendMessage(link(config.gui().buttons().favorites(), "/friendsgui favorites", "Open favorite friends"));
        footer(player);
    }

    public void friends(Player player, int page, boolean favoritesOnly) {
        friends.friends(player, favoritesOnly).whenComplete((list, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            if (list.isEmpty()) {
                messages.send(player, "friend-list-empty");
                return;
            }
            Page<FriendView> view = Page.of(list, page, config.gui().pageSize());
            header(player, favoritesOnly ? config.gui().titles().favorites() : config.gui().titles().friends());
            player.sendMessage(messages.component("friend-list-header", Map.of(
                    "count", String.valueOf(view.totalItems()),
                    "page", String.valueOf(view.page()),
                    "max_page", String.valueOf(view.maxPage()))));
            for (FriendView friend : view.items()) {
                String status = friend.online() ? "online" : "offline";
                Component line = Component.text("- " + friend.username() + " [" + status + "] " + friend.serverName(), NamedTextColor.GRAY)
                        .append(Component.space())
                        .append(link("Msg", "/dm " + friend.username() + " ", "Message " + friend.username()))
                        .append(Component.space())
                        .append(link(friend.favorite() ? "Unfavorite" : "Favorite", "/friend favorite " + friend.username(), "Toggle favorite"))
                        .append(Component.space())
                        .append(link("Note", "/friend note " + friend.username() + " ", friend.note().isBlank() ? "Set note" : friend.note()))
                        .append(Component.space())
                        .append(link("Remove", "/friend remove " + friend.username(), "Remove friend"))
                        .append(Component.space())
                        .append(link("Block", "/friend block " + friend.username(), "Block player"));
                player.sendMessage(line);
            }
            if (view.page() > 1) {
                player.sendMessage(link("Previous", "/friendsgui " + (favoritesOnly ? "favorites" : "friends") + " " + (view.page() - 1), "Previous page"));
            }
            if (view.page() < view.maxPage()) {
                player.sendMessage(link("Next", "/friendsgui " + (favoritesOnly ? "favorites" : "friends") + " " + (view.page() + 1), "Next page"));
            }
            player.sendMessage(link(config.gui().buttons().back(), "/friendsgui", "Back"));
            footer(player);
        });
    }

    public void requests(Player player) {
        friends.requests(player).whenComplete((view, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            header(player, config.gui().titles().requests());
            if (view.incoming().isEmpty() && view.outgoing().isEmpty()) {
                messages.send(player, "requests-empty");
            } else {
                player.sendMessage(messages.component("requests-header", Map.of(
                        "incoming", String.valueOf(view.incoming().size()),
                        "outgoing", String.valueOf(view.outgoing().size()))));
                for (FriendService.RequestLine request : view.incoming()) {
                    player.sendMessage(Component.text("Incoming: " + request.playerName() + " expires " + DurationFormatter.compact(request.remaining()), NamedTextColor.GRAY)
                            .append(Component.space())
                            .append(link("Accept", "/friend accept " + request.playerName(), "Accept request"))
                            .append(Component.space())
                            .append(link("Deny", "/friend deny " + request.playerName(), "Deny request")));
                }
                for (FriendService.RequestLine request : view.outgoing()) {
                    player.sendMessage(Component.text("Outgoing: " + request.playerName() + " expires " + DurationFormatter.compact(request.remaining()), NamedTextColor.GRAY)
                            .append(Component.space())
                            .append(link("Cancel", "/friend cancel " + request.playerName(), "Cancel request")));
                }
            }
            player.sendMessage(link(config.gui().buttons().back(), "/friendsgui", "Back"));
            footer(player);
        });
    }

    public void directMessage(Player player) {
        header(player, config.gui().titles().directMessage());
        player.sendMessage(Component.text("Online players", NamedTextColor.GRAY));
        player.getCurrentServer();
        player.sendMessage(Component.text("Use ", NamedTextColor.GRAY).append(link("/dm <player> <message>", "/dm ", "Start a direct message")));
        footer(player);
    }

    public void privacy(Player player) {
        settings.settings(player).whenComplete((value, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            header(player, config.gui().titles().privacy());
            player.sendMessage(row("Friend requests: " + onOff(value.friendRequestsEnabled()), "/friend toggle"));
            player.sendMessage(optionRow("DMs", value.dmPrivacy().configValue(), "/social privacy dm "));
            player.sendMessage(optionRow("Online", value.onlineStatusVisibility().configValue(), "/social privacy online "));
            player.sendMessage(Component.text("Server: " + value.serverVisibility().configValue(), NamedTextColor.GRAY)
                    .append(Component.space())
                    .append(link("friends", "/social privacy server friends", "Friends can see your server"))
                    .append(Component.space())
                    .append(link("nobody", "/social privacy server nobody", "Hide your server")));
            player.sendMessage(link(config.gui().buttons().back(), "/friendsgui", "Back"));
            footer(player);
        });
    }

    public void blocked(Player player) {
        settings.blockedIgnored(player).whenComplete((view, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            header(player, config.gui().titles().blocked());
            player.sendMessage(Component.text("Blocked", NamedTextColor.YELLOW));
            for (String name : view.blocked()) {
                player.sendMessage(Component.text("- " + name, NamedTextColor.GRAY).append(Component.space()).append(link("Unblock", "/friend unblock " + name, "Unblock")));
            }
            player.sendMessage(Component.text("Ignored", NamedTextColor.YELLOW));
            for (String name : view.ignored()) {
                player.sendMessage(Component.text("- " + name, NamedTextColor.GRAY).append(Component.space()).append(link("Unignore", "/unignore " + name, "Unignore")));
            }
            player.sendMessage(link(config.gui().buttons().back(), "/friendsgui", "Back"));
            footer(player);
        });
    }

    private Component optionRow(String name, String current, String commandPrefix) {
        return Component.text(name + ": " + current, NamedTextColor.GRAY)
                .append(Component.space())
                .append(link("everyone", commandPrefix + "everyone", "Allow everyone"))
                .append(Component.space())
                .append(link("friends", commandPrefix + "friends", "Friends only"))
                .append(Component.space())
                .append(link("nobody", commandPrefix + "nobody", "Allow nobody"));
    }

    private Component row(String label, String command) {
        return Component.text(label, NamedTextColor.GRAY).append(Component.space()).append(link("Toggle", command, "Toggle setting"));
    }

    private Component link(String label, String command, String hover) {
        return Component.text("[" + label + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));
    }

    private String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private void header(Player player, String title) {
        player.sendMessage(messages.component("menu-footer", Map.of()));
        player.sendMessage(messages.component("menu-header", Map.of("title", title)));
    }

    private void footer(Player player) {
        player.sendMessage(messages.component("menu-footer", Map.of()));
    }
}
