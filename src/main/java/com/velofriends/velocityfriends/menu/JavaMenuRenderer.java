package com.velofriends.velocityfriends.menu;

import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.model.FriendView;
import com.velofriends.velocityfriends.service.FriendService;
import com.velofriends.velocityfriends.service.SettingsService;
import com.velofriends.velocityfriends.util.DurationFormatter;
import com.velofriends.velocityfriends.util.Page;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

public final class JavaMenuRenderer {
    private static final Component AUTHOR = Component.text("VelocityFriends");

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
        openBook(player, config.gui().titles().main(), List.of(
                title(config.gui().titles().main())
                        .append(Component.newline())
                        .append(Component.text("Choose an action:", NamedTextColor.DARK_GRAY))
                        .append(Component.newline()).append(Component.newline())
                        .append(menuButton(config.gui().buttons().friends(), "/friendsgui friends", "Open friends"))
                        .append(Component.newline()).append(menuButton(config.gui().buttons().requests(), "/friendsgui requests", "Open requests"))
                        .append(Component.newline()).append(menuButton(config.gui().buttons().directMessage(), "/friendsgui dm", "Message an online player"))
                        .append(Component.newline()).append(menuButton(config.gui().buttons().privacy(), "/friendsgui privacy", "Open privacy settings"))
                        .append(Component.newline()).append(menuButton(config.gui().buttons().blocked(), "/friendsgui blocked", "Open blocked and ignored players"))
                        .append(Component.newline()).append(menuButton(config.gui().buttons().favorites(), "/friendsgui favorites", "Open favorite friends"))
        ));
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
            List<Component> pages = new ArrayList<>();
            Component currentPage = title(favoritesOnly ? config.gui().titles().favorites() : config.gui().titles().friends())
                    .append(Component.newline())
                    .append(Component.text("Page " + view.page() + "/" + view.maxPage() + " • " + view.totalItems() + " friends", NamedTextColor.DARK_GRAY))
                    .append(Component.newline());
            for (FriendView friend : view.items()) {
                currentPage = currentPage.append(Component.newline()).append(friendLine(friend));
            }
            currentPage = currentPage.append(Component.newline()).append(Component.newline()).append(navButtons(view, favoritesOnly ? "favorites" : "friends"))
                    .append(Component.newline()).append(menuButton(config.gui().buttons().back(), "/friendsgui", "Back to social menu"));
            pages.add(currentPage);
            openBook(player, favoritesOnly ? config.gui().titles().favorites() : config.gui().titles().friends(), pages);
        });
    }

    public void requests(Player player) {
        friends.requests(player).whenComplete((view, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            Component page = title(config.gui().titles().requests()).append(Component.newline())
                    .append(Component.text("Incoming: " + view.incoming().size() + " • Outgoing: " + view.outgoing().size(), NamedTextColor.DARK_GRAY))
                    .append(Component.newline());
            if (view.incoming().isEmpty() && view.outgoing().isEmpty()) {
                page = page.append(Component.newline()).append(Component.text("No pending requests.", NamedTextColor.GRAY));
            } else {
                for (FriendService.RequestLine request : view.incoming()) {
                    page = page.append(Component.newline())
                            .append(Component.text("↓ " + request.playerName() + " ", NamedTextColor.GREEN))
                            .append(Component.text(DurationFormatter.compact(request.remaining()), NamedTextColor.DARK_GRAY))
                            .append(Component.newline())
                            .append(smallButton("Accept", "/friend accept " + request.playerName(), "Accept request"))
                            .append(Component.space()).append(smallButton("Deny", "/friend deny " + request.playerName(), "Deny request"));
                }
                for (FriendService.RequestLine request : view.outgoing()) {
                    page = page.append(Component.newline())
                            .append(Component.text("↑ " + request.playerName() + " ", NamedTextColor.YELLOW))
                            .append(Component.text(DurationFormatter.compact(request.remaining()), NamedTextColor.DARK_GRAY))
                            .append(Component.newline())
                            .append(smallButton("Cancel", "/friend cancel " + request.playerName(), "Cancel request"));
                }
            }
            page = page.append(Component.newline()).append(Component.newline()).append(menuButton(config.gui().buttons().back(), "/friendsgui", "Back"));
            openBook(player, config.gui().titles().requests(), List.of(page));
        });
    }

    public void directMessage(Player player) {
        openBook(player, config.gui().titles().directMessage(), List.of(
                title(config.gui().titles().directMessage())
                        .append(Component.newline()).append(Component.text("Start a private message:", NamedTextColor.DARK_GRAY))
                        .append(Component.newline()).append(Component.newline())
                        .append(menuButton("/dm <player> <message>", "/dm ", "Click to prefill a direct message"))
                        .append(Component.newline()).append(Component.newline())
                        .append(menuButton(config.gui().buttons().back(), "/friendsgui", "Back"))
        ));
    }

    public void privacy(Player player) {
        settings.settings(player).whenComplete((value, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            Component page = title(config.gui().titles().privacy()).append(Component.newline()).append(Component.newline())
                    .append(row("Friend requests: " + onOff(value.friendRequestsEnabled()), "/friend toggle"))
                    .append(Component.newline()).append(optionRow("DMs", value.dmPrivacy().configValue(), "/social privacy dm "))
                    .append(Component.newline()).append(optionRow("Online", value.onlineStatusVisibility().configValue(), "/social privacy online "))
                    .append(Component.newline()).append(Component.text("Server: " + value.serverVisibility().configValue(), NamedTextColor.DARK_GRAY))
                    .append(Component.newline()).append(smallButton("friends", "/social privacy server friends", "Friends can see your server"))
                    .append(Component.space()).append(smallButton("nobody", "/social privacy server nobody", "Hide your server"))
                    .append(Component.newline()).append(Component.newline()).append(menuButton(config.gui().buttons().back(), "/friendsgui", "Back"));
            openBook(player, config.gui().titles().privacy(), List.of(page));
        });
    }

    public void blocked(Player player) {
        settings.blockedIgnored(player).whenComplete((view, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            Component page = title(config.gui().titles().blocked()).append(Component.newline())
                    .append(Component.text("Blocked", NamedTextColor.RED)).append(Component.newline());
            if (view.blocked().isEmpty()) page = page.append(Component.text("None", NamedTextColor.GRAY)).append(Component.newline());
            for (String name : view.blocked()) page = page.append(Component.text(name + " ", NamedTextColor.DARK_GRAY)).append(smallButton("Unblock", "/friend unblock " + name, "Unblock")).append(Component.newline());
            page = page.append(Component.newline()).append(Component.text("Ignored", NamedTextColor.YELLOW)).append(Component.newline());
            if (view.ignored().isEmpty()) page = page.append(Component.text("None", NamedTextColor.GRAY)).append(Component.newline());
            for (String name : view.ignored()) page = page.append(Component.text(name + " ", NamedTextColor.DARK_GRAY)).append(smallButton("Unignore", "/unignore " + name, "Unignore")).append(Component.newline());
            page = page.append(Component.newline()).append(menuButton(config.gui().buttons().back(), "/friendsgui", "Back"));
            openBook(player, config.gui().titles().blocked(), List.of(page));
        });
    }

    private Component friendLine(FriendView friend) {
        NamedTextColor statusColor = friend.online() ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(friend.username(), NamedTextColor.BLACK, TextDecoration.BOLD)
                .append(Component.text(" • " + (friend.online() ? "online" : "offline"), statusColor))
                .append(Component.newline())
                .append(Component.text(friend.serverName(), NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(smallButton("Msg", "/dm " + friend.username() + " ", "Message " + friend.username()))
                .append(Component.space()).append(smallButton(friend.favorite() ? "Unfav" : "Fav", "/friend favorite " + friend.username(), "Toggle favorite"))
                .append(Component.space()).append(smallButton("Note", "/friend note " + friend.username() + " ", friend.note().isBlank() ? "Set note" : friend.note()))
                .append(Component.space()).append(smallButton("Remove", "/friend remove " + friend.username(), "Remove friend"))
                .append(Component.space()).append(smallButton("Block", "/friend block " + friend.username(), "Block player"));
    }

    private Component navButtons(Page<?> view, String section) {
        Component result = Component.empty();
        if (view.page() > 1) result = result.append(smallButton("◀ Previous", "/friendsgui " + section + " " + (view.page() - 1), "Previous page"));
        if (view.page() > 1 && view.page() < view.maxPage()) result = result.append(Component.space());
        if (view.page() < view.maxPage()) result = result.append(smallButton("Next ▶", "/friendsgui " + section + " " + (view.page() + 1), "Next page"));
        return result;
    }

    private Component optionRow(String name, String current, String commandPrefix) {
        return Component.text(name + ": " + current, NamedTextColor.DARK_GRAY)
                .append(Component.newline())
                .append(smallButton("everyone", commandPrefix + "everyone", "Allow everyone"))
                .append(Component.space()).append(smallButton("friends", commandPrefix + "friends", "Friends only"))
                .append(Component.space()).append(smallButton("nobody", commandPrefix + "nobody", "Allow nobody"));
    }

    private Component row(String label, String command) {
        return Component.text(label, NamedTextColor.DARK_GRAY).append(Component.space()).append(smallButton("Toggle", command, "Toggle setting"));
    }

    private Component menuButton(String label, String command, String hover) {
        return Component.text("▶ " + label, NamedTextColor.BLUE, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));
    }

    private Component smallButton(String label, String command, String hover) {
        return Component.text("[" + label + "]", NamedTextColor.DARK_AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));
    }

    private Component title(String title) {
        return Component.text(title, NamedTextColor.DARK_BLUE, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("────────────", NamedTextColor.GRAY));
    }

    private void openBook(Player player, String title, List<Component> pages) {
        player.openBook(Book.book(Component.text(title), AUTHOR, pages));
    }

    private String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
