package com.velofriends.velocityfriends.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.menu.SocialMenuService;
import com.velofriends.velocityfriends.model.PrivacyLevel;
import com.velofriends.velocityfriends.model.ServerVisibility;
import com.velofriends.velocityfriends.service.SettingsService;

import java.util.List;

public final class SocialMenuCommand implements SimpleCommand {
    private final SocialMenuService menus;
    private final SettingsService settings;
    private final MessageManager messages;

    public SocialMenuCommand(SocialMenuService menus, SettingsService settings, MessageManager messages) {
        this.menus = menus;
        this.settings = settings;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            messages.send(invocation.source(), "player-only");
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 0) {
            menus.main(player);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "friends" -> menus.friends(player, CommandUtil.page(args, 1), false);
            case "favorites" -> menus.friends(player, CommandUtil.page(args, 1), true);
            case "requests" -> menus.requests(player);
            case "dm", "message" -> menus.directMessage(player);
            case "privacy" -> handlePrivacy(player, args);
            case "blocked", "ignored" -> menus.blocked(player);
            default -> menus.main(player);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0].toLowerCase();
            return List.of("friends", "favorites", "requests", "dm", "privacy", "blocked").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    private void handlePrivacy(Player player, String[] args) {
        if (args.length < 3) {
            menus.privacy(player);
            return;
        }
        String target = args[1].toLowerCase();
        String value = args[2].toLowerCase();
        switch (target) {
            case "dm", "dms" -> CommandUtil.sendResult(player, messages, settings.setDmPrivacy(player, PrivacyLevel.fromConfig(value, PrivacyLevel.EVERYONE)));
            case "online" -> CommandUtil.sendResult(player, messages, settings.setOnlinePrivacy(player, PrivacyLevel.fromConfig(value, PrivacyLevel.FRIENDS_ONLY)));
            case "server" -> CommandUtil.sendResult(player, messages, settings.setServerVisibility(player, ServerVisibility.fromConfig(value, ServerVisibility.FRIENDS)));
            default -> menus.privacy(player);
        }
    }
}
