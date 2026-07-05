package com.velofriends.velocityfriends.menu;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.model.FriendView;
import com.velofriends.velocityfriends.service.FriendService;
import com.velofriends.velocityfriends.service.SettingsService;
import com.velofriends.velocityfriends.util.DurationFormatter;
import com.velofriends.velocityfriends.util.Page;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class JavaMenuRenderer {
    private final ProxyServer proxy;
    private final FriendService friends;
    private final SettingsService settings;
    private final MessageManager messages;
    private final PluginConfig config;

    public JavaMenuRenderer(ProxyServer proxy, FriendService friends, SettingsService settings,
                            MessageManager messages, PluginConfig config) {
        this.proxy = proxy;
        this.friends = friends;
        this.settings = settings;
        this.messages = messages;
        this.config = config;
    }

    public void main(Player player) {
        sendMenu(player, List.of(
                title(config.gui().titles().main()),
                menuButton(config.gui().buttons().friends(), "/friendsgui friends", "Open friends"),
                menuButton(config.gui().buttons().requests(), "/friendsgui requests", "Open requests"),
                menuButton(config.gui().buttons().directMessage(), "/friendsgui dm", "Message an online player"),
                menuButton(config.gui().buttons().privacy(), "/friendsgui privacy", "Open privacy settings"),
                menuButton(config.gui().buttons().blocked(), "/friendsgui blocked", "Open blocked and ignored players"),
                menuButton(config.gui().buttons().favorites(), "/friendsgui favorites", "Open favorite friends")
        ));
    }

    public void friends(Player player, int page, boolean favoritesOnly) {
        friends.friends(player, favoritesOnly).whenComplete((list, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            if (list.isEmpty()) {
                messages.send(player, favoritesOnly ? "favorite-list-empty" : "friend-list-empty");
                return;
            }
            Page<FriendView> view = Page.of(list, page, config.gui().pageSize());
            List<Component> lines = new ArrayList<>();
            lines.add(title(favoritesOnly ? config.gui().titles().favorites() : config.gui().titles().friends()));
            lines.add(Component.text("Page " + view.page() + "/" + view.maxPage() + " - " + view.totalItems() + " friends", NamedTextColor.YELLOW));
            for (FriendView friend : view.items()) {
                lines.add(friendLine(friend));
            }
            lines.add(navButtons(view, favoritesOnly ? "favorites" : "friends"));
            lines.add(menuButton(config.gui().buttons().back(), "/friendsgui", "Back to social menu"));
            sendMenu(player, lines);
        });
    }

    public void requests(Player player) {
        friends.requests(player).whenComplete((view, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(title(config.gui().titles().requests()));
            lines.add(Component.text("Incoming: " + view.incoming().size() + " | Outgoing: " + view.outgoing().size(), NamedTextColor.AQUA));
            if (view.incoming().isEmpty() && view.outgoing().isEmpty()) {
                lines.add(Component.text("No pending requests.", NamedTextColor.YELLOW));
            } else {
                for (FriendService.RequestLine request : view.incoming()) {
                    lines.add(Component.text(request.playerName() + " - " + DurationFormatter.compact(request.remaining()) + " ", NamedTextColor.GREEN)
                            .append(smallButton("Accept", "/friend accept " + request.playerName(), "Accept request"))
                            .append(Component.space())
                            .append(smallButton("Deny", "/friend deny " + request.playerName(), "Deny request")));
                }
                for (FriendService.RequestLine request : view.outgoing()) {
                    lines.add(Component.text(request.playerName() + " - " + DurationFormatter.compact(request.remaining()) + " ", NamedTextColor.YELLOW)
                            .append(smallButton("Cancel", "/friend cancel " + request.playerName(), "Cancel request")));
                }
            }
            lines.add(menuButton(config.gui().buttons().back(), "/friendsgui", "Back"));
            sendMenu(player, lines);
        });
    }

    public void directMessage(Player player) {
        List<Player> onlinePlayers = proxy.getAllPlayers().stream()
                .filter(online -> !online.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Component> lines = new ArrayList<>();
        lines.add(title(config.gui().titles().directMessage()));
        if (onlinePlayers.isEmpty()) {
            lines.add(Component.text("Nobody else is online.", NamedTextColor.YELLOW));
        } else {
            for (Player online : onlinePlayers) {
                lines.add(suggestButton(online.getUsername(), "/dm " + online.getUsername() + " ", "Type a message"));
            }
        }
        lines.add(menuButton(config.gui().buttons().back(), "/friendsgui", "Back"));
        sendMenu(player, lines);
    }

    public void privacy(Player player) {
        settings.settings(player).whenComplete((value, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            sendMenu(player, List.of(
                    title(config.gui().titles().privacy()),
                    row("Friend requests: " + onOff(value.friendRequestsEnabled()), "/friend toggle"),
                    optionRow("DMs", value.dmPrivacy().configValue(), "/social privacy dm "),
                    optionRow("Online", value.onlineStatusVisibility().configValue(), "/social privacy online "),
                    Component.text("Server: " + value.serverVisibility().configValue(), NamedTextColor.WHITE)
                            .append(Component.space())
                            .append(smallButton("friends", "/social privacy server friends", "Friends can see your server"))
                            .append(Component.space())
                            .append(smallButton("nobody", "/social privacy server nobody", "Hide your server")),
                    menuButton(config.gui().buttons().back(), "/friendsgui", "Back")
            ));
        });
    }

    public void blocked(Player player) {
        settings.blockedIgnored(player).whenComplete((view, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(title(config.gui().titles().blocked()));
            lines.add(Component.text("Blocked", NamedTextColor.RED));
            if (view.blocked().isEmpty()) {
                lines.add(Component.text("None", NamedTextColor.YELLOW));
            }
            for (String name : view.blocked()) {
                lines.add(Component.text(name + " ", NamedTextColor.WHITE)
                        .append(smallButton("Unblock", "/friend unblock " + name, "Unblock")));
            }
            lines.add(Component.text("Ignored", NamedTextColor.YELLOW));
            if (view.ignored().isEmpty()) {
                lines.add(Component.text("None", NamedTextColor.YELLOW));
            }
            for (String name : view.ignored()) {
                lines.add(Component.text(name + " ", NamedTextColor.WHITE)
                        .append(smallButton("Unignore", "/unignore " + name, "Unignore")));
            }
            lines.add(menuButton(config.gui().buttons().back(), "/friendsgui", "Back"));
            sendMenu(player, lines);
        });
    }

    private Component friendLine(FriendView friend) {
        NamedTextColor statusColor = friend.online() ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(friend.username(), NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(" - " + (friend.online() ? "online" : "offline"), statusColor))
                .append(Component.text(" - ", NamedTextColor.GOLD))
                .append(Component.text(friend.serverName() + " ", NamedTextColor.WHITE))
                .append(suggestButton("Msg", "/dm " + friend.username() + " ", "Message " + friend.username(), NamedTextColor.GREEN))
                .append(Component.space()).append(smallButton(friend.favorite() ? "Unfav" : "Fav", "/friend favorite " + friend.username(), "Toggle favorite", NamedTextColor.GOLD))
                .append(Component.space()).append(suggestButton("Note", "/friend note " + friend.username() + " ", friend.note().isBlank() ? "Set note" : friend.note(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.space()).append(smallButton("Remove", "/friend remove " + friend.username(), "Remove friend", NamedTextColor.RED))
                .append(Component.space()).append(smallButton("Block", "/friend block " + friend.username(), "Block player", NamedTextColor.DARK_RED));
    }

    private Component navButtons(Page<?> view, String section) {
        Component result = Component.empty();
        if (view.page() > 1) {
            result = result.append(smallButton("Previous", "/friendsgui " + section + " " + (view.page() - 1), "Previous page"));
        }
        if (view.page() > 1 && view.page() < view.maxPage()) {
            result = result.append(Component.space());
        }
        if (view.page() < view.maxPage()) {
            result = result.append(smallButton("Next", "/friendsgui " + section + " " + (view.page() + 1), "Next page"));
        }
        return result;
    }

    private Component optionRow(String name, String current, String commandPrefix) {
        return Component.text(name + ": " + current, NamedTextColor.WHITE)
                .append(Component.space())
                .append(smallButton("everyone", commandPrefix + "everyone", "Allow everyone"))
                .append(Component.space()).append(smallButton("friends", commandPrefix + "friends", "Friends only"))
                .append(Component.space()).append(smallButton("nobody", commandPrefix + "nobody", "Allow nobody"));
    }

    private Component row(String label, String command) {
        return Component.text(label, NamedTextColor.WHITE).append(Component.space()).append(smallButton("Toggle", command, "Toggle setting"));
    }

    private Component menuButton(String label, String command, String hover) {
        return Component.text("> " + label, NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GOLD)));
    }

    private Component smallButton(String label, String command, String hover) {
        return smallButton(label, command, hover, NamedTextColor.AQUA);
    }

    private Component smallButton(String label, String command, String hover, NamedTextColor color) {
        return Component.text("[" + label + "]", color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GOLD)));
    }

    private Component suggestButton(String label, String command, String hover) {
        return suggestButton(label, command, hover, NamedTextColor.LIGHT_PURPLE);
    }

    private Component suggestButton(String label, String command, String hover, NamedTextColor color) {
        return Component.text("[" + label + "]", color)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GOLD)));
    }

    private Component title(String title) {
        return Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" | ", NamedTextColor.WHITE))
                .append(Component.text("VelocityFriends", NamedTextColor.AQUA, TextDecoration.BOLD));
    }

    private void sendMenu(Player player, List<Component> lines) {
        player.sendMessage(Component.empty());
        for (Component line : lines) {
            player.sendMessage(line);
        }
        player.sendMessage(Component.empty());
    }

    private String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
