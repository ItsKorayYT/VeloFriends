package com.velofriends.velocityfriends.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.service.MessagingService;

import java.util.List;

public final class DirectMessageCommand implements SimpleCommand {
    private final MessagingService messaging;
    private final MessageManager messages;

    public DirectMessageCommand(MessagingService messaging, MessageManager messages) {
        this.messaging = messaging;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            messages.send(invocation.source(), "player-only");
            return;
        }
        String[] args = invocation.arguments();
        if (args.length < 2) {
            messages.send(player, "usage-dm");
            return;
        }
        CommandUtil.sendResult(player, messages, messaging.send(player, args[0], CommandUtil.join(args, 1)));
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
