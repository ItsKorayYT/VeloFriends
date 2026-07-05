package com.velofriends.velocityfriends.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.menu.SocialMenuService;
import com.velofriends.velocityfriends.service.FriendService;

import java.util.List;

public final class FriendCommand implements SimpleCommand {
    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "accept", "deny", "cancel", "list", "requests", "block", "unblock", "toggle", "favorite", "note", "gui");
    private final FriendService friends;
    private final SocialMenuService menus;
    private final MessageManager messages;

    public FriendCommand(FriendService friends, SocialMenuService menus, MessageManager messages) {
        this.friends = friends;
        this.menus = menus;
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
            if (invocation.alias().equalsIgnoreCase("friends")) {
                menus.friends(player, 1, false);
            } else {
                menus.main(player);
            }
            return;
        }
        switch (args[0].toLowerCase()) {
            case "add" -> requireTarget(player, args, () -> CommandUtil.sendResult(player, messages, friends.add(player, args[1])));
            case "remove" -> requireTarget(player, args, () -> CommandUtil.sendResult(player, messages, friends.remove(player, args[1])));
            case "accept" -> requireTarget(player, args, () -> CommandUtil.sendResult(player, messages, friends.accept(player, args[1])));
            case "deny" -> requireTarget(player, args, () -> CommandUtil.sendResult(player, messages, friends.deny(player, args[1])));
            case "cancel" -> requireTarget(player, args, () -> CommandUtil.sendResult(player, messages, friends.cancel(player, args[1])));
            case "block" -> requireTarget(player, args, () -> CommandUtil.sendResult(player, messages, friends.block(player, args[1])));
            case "unblock" -> requireTarget(player, args, () -> CommandUtil.sendResult(player, messages, friends.unblock(player, args[1])));
            case "favorite" -> requireTarget(player, args, () -> CommandUtil.sendResult(player, messages, friends.favorite(player, args[1])));
            case "note" -> {
                if (args.length < 3) {
                    messages.send(player, "usage-friend");
                    return;
                }
                CommandUtil.sendResult(player, messages, friends.note(player, args[1], CommandUtil.join(args, 2)));
            }
            case "list" -> menus.friends(player, CommandUtil.page(args, 1), false);
            case "requests" -> menus.requests(player);
            case "toggle" -> CommandUtil.sendResult(player, messages, friends.toggleRequests(player));
            case "gui" -> menus.main(player);
            default -> messages.send(player, "unknown-command");
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityfriends.command.friend");
    }

    private void requireTarget(Player player, String[] args, Runnable runnable) {
        if (args.length < 2) {
            messages.send(player, "usage-friend");
            return;
        }
        runnable.run();
    }
}
