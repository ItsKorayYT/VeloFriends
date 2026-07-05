package com.velofriends.velocityfriends.menu;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.model.FriendView;
import com.velofriends.velocityfriends.model.PrivacyLevel;
import com.velofriends.velocityfriends.model.ServerVisibility;
import com.velofriends.velocityfriends.service.FriendService;
import com.velofriends.velocityfriends.service.MessagingService;
import com.velofriends.velocityfriends.service.SettingsService;
import com.velofriends.velocityfriends.util.DurationFormatter;
import com.velofriends.velocityfriends.util.OperationResult;
import com.velofriends.velocityfriends.util.Page;

import java.util.ArrayList;
import java.util.List;

public final class SocialMenuService {
    private final ProxyServer proxy;
    private final PluginConfig config;
    private final MessageManager messages;
    private final FloodgateIntegration floodgate;
    private final JavaMenuRenderer javaMenus;
    private final FriendService friends;
    private final MessagingService messaging;
    private final SettingsService settings;

    public SocialMenuService(ProxyServer proxy, PluginConfig config, MessageManager messages,
                             FloodgateIntegration floodgate, JavaMenuRenderer javaMenus, FriendService friends,
                             MessagingService messaging, SettingsService settings) {
        this.proxy = proxy;
        this.config = config;
        this.messages = messages;
        this.floodgate = floodgate;
        this.javaMenus = javaMenus;
        this.friends = friends;
        this.messaging = messaging;
        this.settings = settings;
    }

    public void main(Player player) {
        if (bedrock(player)) {
            List<FloodgateIntegration.FormButton> buttons = List.of(
                    new FloodgateIntegration.FormButton(config.gui().buttons().addFriend(), () -> addFriend(player)),
                    new FloodgateIntegration.FormButton(config.gui().buttons().friends(), () -> friends(player, 1, false)),
                    new FloodgateIntegration.FormButton(config.gui().buttons().requests(), () -> requests(player)),
                    new FloodgateIntegration.FormButton(config.gui().buttons().directMessage(), () -> directMessage(player)),
                    new FloodgateIntegration.FormButton(config.gui().buttons().privacy(), () -> privacy(player)),
                    new FloodgateIntegration.FormButton(config.gui().buttons().blocked(), () -> blocked(player)),
                    new FloodgateIntegration.FormButton(config.gui().buttons().favorites(), () -> friends(player, 1, true)),
                    new FloodgateIntegration.FormButton(config.gui().buttons().close(), () -> { })
            );
            if (floodgate.sendSimpleForm(player, config.gui().titles().main(), "Select a social action.", buttons)) {
                return;
            }
            formsUnavailable(player);
            return;
        }
        javaMenus.main(player);
    }

    public void friends(Player player, int page, boolean favoritesOnly) {
        if (!bedrock(player)) {
            javaMenus.friends(player, page, favoritesOnly);
            return;
        }
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
            List<FloodgateIntegration.FormButton> buttons = new ArrayList<>();
            for (FriendView friend : view.items()) {
                buttons.add(new FloodgateIntegration.FormButton(friend.username() + " - " + (friend.online() ? "online" : "offline"),
                        () -> friendActions(player, friend)));
            }
            if (view.page() > 1) {
                buttons.add(new FloodgateIntegration.FormButton("Previous", () -> friends(player, view.page() - 1, favoritesOnly)));
            }
            if (view.page() < view.maxPage()) {
                buttons.add(new FloodgateIntegration.FormButton("Next", () -> friends(player, view.page() + 1, favoritesOnly)));
            }
            buttons.add(new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> main(player)));
            if (!floodgate.sendSimpleForm(player, favoritesOnly ? config.gui().titles().favorites() : config.gui().titles().friends(),
                    "Page " + view.page() + "/" + view.maxPage(), buttons)) {
                formsUnavailable(player);
            }
        });
    }

    public void requests(Player player) {
        if (!bedrock(player)) {
            javaMenus.requests(player);
            return;
        }
        friends.requests(player).whenComplete((view, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<FloodgateIntegration.FormButton> buttons = new ArrayList<>();
            for (FriendService.RequestLine request : view.incoming()) {
                buttons.add(new FloodgateIntegration.FormButton("Accept " + request.playerName(),
                        () -> sendResult(player, friends.accept(player, request.playerName()))));
                buttons.add(new FloodgateIntegration.FormButton("Deny " + request.playerName(),
                        () -> sendResult(player, friends.deny(player, request.playerName()))));
            }
            for (FriendService.RequestLine request : view.outgoing()) {
                buttons.add(new FloodgateIntegration.FormButton("Cancel " + request.playerName(),
                        () -> sendResult(player, friends.cancel(player, request.playerName()))));
            }
            buttons.add(new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> main(player)));
            String content = "Incoming: " + view.incoming().size() + "\nOutgoing: " + view.outgoing().size();
            if (!floodgate.sendSimpleForm(player, config.gui().titles().requests(), content, buttons)) {
                formsUnavailable(player);
            }
        });
    }

    private void addFriend(Player player) {
        boolean sent = floodgate.sendInputForm(player, config.gui().buttons().addFriend(), "Player", "Type a player name...", target -> {
            if (target != null && !target.isBlank()) {
                sendResult(player, friends.add(player, target.strip()));
            }
        });
        if (!sent) {
            formsUnavailable(player);
        }
    }

    public void directMessage(Player player) {
        if (!bedrock(player)) {
            javaMenus.directMessage(player);
            return;
        }
        List<FloodgateIntegration.FormButton> buttons = new ArrayList<>();
        for (Player online : proxy.getAllPlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                buttons.add(new FloodgateIntegration.FormButton(online.getUsername(), () -> messageInput(player, online.getUsername())));
            }
        }
        buttons.add(new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> main(player)));
        if (!floodgate.sendSimpleForm(player, config.gui().titles().directMessage(), "Choose an online player.", buttons)) {
            formsUnavailable(player);
        }
    }

    public void privacy(Player player) {
        if (!bedrock(player)) {
            javaMenus.privacy(player);
            return;
        }
        settings.settings(player).whenComplete((current, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<FloodgateIntegration.FormButton> buttons = List.of(
                    new FloodgateIntegration.FormButton("Friend Requests: " + (current.friendRequestsEnabled() ? "on" : "off"),
                            () -> privacyFriendRequests(player)),
                    new FloodgateIntegration.FormButton("Direct Messages: " + current.dmPrivacy().configValue(),
                            () -> privacyDirectMessages(player)),
                    new FloodgateIntegration.FormButton("Online Status: " + current.onlineStatusVisibility().configValue(),
                            () -> privacyOnlineStatus(player)),
                    new FloodgateIntegration.FormButton("Visible Server: " + current.serverVisibility().configValue(),
                            () -> privacyServerVisibility(player)),
                    new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> main(player))
            );
            if (!floodgate.sendSimpleForm(player, config.gui().titles().privacy(), "Choose a privacy category.", buttons)) {
                formsUnavailable(player);
            }
        });
    }

    private void privacyFriendRequests(Player player) {
        settings.settings(player).whenComplete((current, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<FloodgateIntegration.FormButton> buttons = List.of(
                    new FloodgateIntegration.FormButton(current.friendRequestsEnabled() ? "Turn off" : "Turn on",
                            () -> sendResult(player, friends.toggleRequests(player))),
                    new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> privacy(player))
            );
            if (!floodgate.sendSimpleForm(player, "Friend Requests", "Current: " + (current.friendRequestsEnabled() ? "on" : "off"), buttons)) {
                formsUnavailable(player);
            }
        });
    }

    private void privacyDirectMessages(Player player) {
        settings.settings(player).whenComplete((current, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<FloodgateIntegration.FormButton> buttons = List.of(
                    new FloodgateIntegration.FormButton("Everyone", () -> sendResult(player, settings.setDmPrivacy(player, PrivacyLevel.EVERYONE))),
                    new FloodgateIntegration.FormButton("Friends only", () -> sendResult(player, settings.setDmPrivacy(player, PrivacyLevel.FRIENDS_ONLY))),
                    new FloodgateIntegration.FormButton("Nobody", () -> sendResult(player, settings.setDmPrivacy(player, PrivacyLevel.NOBODY))),
                    new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> privacy(player))
            );
            if (!floodgate.sendSimpleForm(player, "Direct Messages", "Current: " + current.dmPrivacy().configValue(), buttons)) {
                formsUnavailable(player);
            }
        });
    }

    private void privacyOnlineStatus(Player player) {
        settings.settings(player).whenComplete((current, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<FloodgateIntegration.FormButton> buttons = List.of(
                    new FloodgateIntegration.FormButton("Everyone", () -> sendResult(player, settings.setOnlinePrivacy(player, PrivacyLevel.EVERYONE))),
                    new FloodgateIntegration.FormButton("Friends only", () -> sendResult(player, settings.setOnlinePrivacy(player, PrivacyLevel.FRIENDS_ONLY))),
                    new FloodgateIntegration.FormButton("Nobody", () -> sendResult(player, settings.setOnlinePrivacy(player, PrivacyLevel.NOBODY))),
                    new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> privacy(player))
            );
            if (!floodgate.sendSimpleForm(player, "Online Status", "Current: " + current.onlineStatusVisibility().configValue(), buttons)) {
                formsUnavailable(player);
            }
        });
    }

    private void privacyServerVisibility(Player player) {
        settings.settings(player).whenComplete((current, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<FloodgateIntegration.FormButton> buttons = List.of(
                    new FloodgateIntegration.FormButton("Friends only", () -> sendResult(player, settings.setServerVisibility(player, ServerVisibility.FRIENDS))),
                    new FloodgateIntegration.FormButton("Nobody", () -> sendResult(player, settings.setServerVisibility(player, ServerVisibility.NOBODY))),
                    new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> privacy(player))
            );
            if (!floodgate.sendSimpleForm(player, "Visible Server", "Current: " + current.serverVisibility().configValue(), buttons)) {
                formsUnavailable(player);
            }
        });
    }

    public void blocked(Player player) {
        if (!bedrock(player)) {
            javaMenus.blocked(player);
            return;
        }
        settings.blockedIgnored(player).whenComplete((view, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            List<FloodgateIntegration.FormButton> buttons = new ArrayList<>();
            for (String name : view.blocked()) {
                buttons.add(new FloodgateIntegration.FormButton("Unblock " + name, () -> sendResult(player, friends.unblock(player, name))));
            }
            for (String name : view.ignored()) {
                buttons.add(new FloodgateIntegration.FormButton("Unignore " + name, () -> sendResult(player, messaging.unignore(player, name))));
            }
            buttons.add(new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> main(player)));
            if (!floodgate.sendSimpleForm(player, config.gui().titles().blocked(), "Blocked: " + view.blocked().size() + "\nIgnored: " + view.ignored().size(), buttons)) {
                formsUnavailable(player);
            }
        });
    }

    private void friendActions(Player player, FriendView friend) {
        List<FloodgateIntegration.FormButton> buttons = List.of(
                new FloodgateIntegration.FormButton("Message", () -> messageInput(player, friend.username())),
                new FloodgateIntegration.FormButton(friend.favorite() ? "Unfavorite" : "Favorite", () -> sendResult(player, friends.favorite(player, friend.username()))),
                new FloodgateIntegration.FormButton("Remove", () -> sendResult(player, friends.remove(player, friend.username()))),
                new FloodgateIntegration.FormButton("Block", () -> sendResult(player, friends.block(player, friend.username()))),
                new FloodgateIntegration.FormButton(config.gui().buttons().back(), () -> friends(player, 1, false))
        );
        if (!floodgate.sendSimpleForm(player, friend.username(), "Server: " + friend.serverName() + "\nNote: " + friend.note(), buttons)) {
            formsUnavailable(player);
        }
    }

    private void messageInput(Player player, String target) {
        boolean sent = floodgate.sendInputForm(player, "Message " + target, "Message", "Type a message...", message -> {
            if (message != null && !message.isBlank()) {
                sendResult(player, messaging.send(player, target, message));
            }
        });
        if (!sent) {
            formsUnavailable(player);
        }
    }

    private boolean bedrock(Player player) {
        return config.gui().bedrockFormsEnabled() && floodgate.isBedrock(player);
    }

    private void formsUnavailable(Player player) {
        messages.send(player, "bedrock-forms-unavailable");
    }

    private void sendResult(Player player, java.util.concurrent.CompletableFuture<OperationResult> future) {
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                messages.send(player, "storage-error");
                return;
            }
            if (!result.messageKey().isBlank()) {
                messages.send(player, result.messageKey(), result.placeholders());
            }
        });
    }
}
