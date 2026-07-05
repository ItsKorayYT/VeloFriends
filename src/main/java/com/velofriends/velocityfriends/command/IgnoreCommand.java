package com.velofriends.velocityfriends.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.service.MessagingService;

import java.util.List;

public final class IgnoreCommand implements SimpleCommand {
    private final MessagingService messaging;
    private final MessageManager messages;
    private final boolean ignore;

    public IgnoreCommand(MessagingService messaging, MessageManager messages, boolean ignore) {
        this.messaging = messaging;
        this.messages = messages;
        this.ignore = ignore;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            messages.send(invocation.source(), "player-only");
            return;
        }
        String[] args = invocation.arguments();
        if (args.length < 1) {
            messages.send(player, ignore ? "usage-dm" : "usage-dm");
            return;
        }
        CommandUtil.sendResult(player, messages, ignore ? messaging.ignore(player, args[0]) : messaging.unignore(player, args[0]));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityfriends.command.dm");
    }
}
