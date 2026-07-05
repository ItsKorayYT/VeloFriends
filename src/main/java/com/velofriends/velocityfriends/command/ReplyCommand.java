package com.velofriends.velocityfriends.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.service.MessagingService;

import java.util.List;

public final class ReplyCommand implements SimpleCommand {
    private final MessagingService messaging;
    private final MessageManager messages;

    public ReplyCommand(MessagingService messaging, MessageManager messages) {
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
        if (args.length < 1) {
            messages.send(player, "usage-reply");
            return;
        }
        CommandUtil.sendResult(player, messages, messaging.reply(player, CommandUtil.join(args, 0)));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }
}
