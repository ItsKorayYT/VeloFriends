package com.velofriends.velocityfriends.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velofriends.velocityfriends.config.ConfigManager;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.menu.FloodgateIntegration;
import com.velofriends.velocityfriends.service.FriendService;
import com.velofriends.velocityfriends.storage.Database;
import com.velofriends.velocityfriends.storage.SocialRepository;
import com.velofriends.velocityfriends.util.OperationResult;

import java.util.List;
import java.util.Map;

public final class AdminCommand implements SimpleCommand {
    private final ConfigManager config;
    private final MessageManager messages;
    private final Database database;
    private final FloodgateIntegration floodgate;
    private final FriendService friends;
    private final String version;
    private final Runnable reloadHook;

    public AdminCommand(ConfigManager config, MessageManager messages, Database database, FloodgateIntegration floodgate,
                        FriendService friends, String version, Runnable reloadHook) {
        this.config = config;
        this.messages = messages;
        this.database = database;
        this.floodgate = floodgate;
        this.friends = friends;
        this.version = version;
        this.reloadHook = reloadHook;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            messages.send(invocation.source(), "usage-admin");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!check(invocation.source(), "velocityfriends.admin.reload")) return;
                reloadHook.run();
                messages.send(invocation.source(), "reloaded");
            }
            case "info" -> {
                if (!check(invocation.source(), "velocityfriends.admin.reload")) return;
                messages.send(invocation.source(), "info", Map.of(
                        "version", version,
                        "storage", database.dialect(),
                        "floodgate", floodgate.available() ? "installed" : "missing"));
            }
            case "debug" -> {
                if (!check(invocation.source(), "velocityfriends.admin.spy")) return;
                if (args.length < 2) {
                    messages.send(invocation.source(), "usage-admin");
                    return;
                }
                friends.debug(args[1]).whenComplete((debug, throwable) -> {
                    if (throwable != null) {
                        messages.send(invocation.source(), "storage-error");
                        return;
                    }
                    messages.send(invocation.source(), "debug-player", Map.of(
                            "player", args[1],
                            "count", String.valueOf(debug.friends()),
                            "requests", String.valueOf(debug.requests()),
                            "ignored", String.valueOf(debug.ignored()),
                            "blocked", String.valueOf(debug.blocked()),
                            "settings", debug.settingsState()));
                });
            }
            case "forceadd" -> {
                if (!check(invocation.source(), "velocityfriends.admin.reload")) return;
                if (args.length < 3) {
                    messages.send(invocation.source(), "usage-admin");
                    return;
                }
                CommandUtil.sendResult(invocation.source(), messages, friends.forceAdd(args[1], args[2]));
            }
            case "forceremove" -> {
                if (!check(invocation.source(), "velocityfriends.admin.reload")) return;
                if (args.length < 3) {
                    messages.send(invocation.source(), "usage-admin");
                    return;
                }
                CommandUtil.sendResult(invocation.source(), messages, friends.forceRemove(args[1], args[2]));
            }
            case "purgeold" -> {
                if (!check(invocation.source(), "velocityfriends.admin.reload")) return;
                if (args.length < 2) {
                    messages.send(invocation.source(), "usage-admin");
                    return;
                }
                try {
                    CommandUtil.sendResult(invocation.source(), messages, friends.purgeOld(Integer.parseInt(args[1])));
                } catch (NumberFormatException exception) {
                    messages.send(invocation.source(), "usage-admin");
                }
            }
            case "migrate" -> {
                if (!check(invocation.source(), "velocityfriends.admin.reload")) return;
                messages.send(invocation.source(), "migrate-complete");
            }
            default -> messages.send(invocation.source(), "usage-admin");
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0].toLowerCase();
            return List.of("reload", "info", "debug", "forceadd", "forceremove", "purgeold", "migrate").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityfriends.admin.reload")
                || invocation.source().hasPermission("velocityfriends.admin.spy");
    }

    private boolean check(CommandSource source, String permission) {
        if (source.hasPermission(permission)) {
            return true;
        }
        messages.send(source, "no-permission");
        return false;
    }
}
