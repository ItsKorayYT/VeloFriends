package com.velofriends.velocityfriends.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.service.MessagingService;

import java.util.List;

public final class SocialSpyCommand implements SimpleCommand {
    private final MessagingService messaging;
    private final MessageManager messages;

    public SocialSpyCommand(MessagingService messaging, MessageManager messages) {
        this.messaging = messaging;
        this.messages = messages;
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.source() instanceof Player player) {
            CommandUtil.sendResult(player, messages, messaging.toggleSocialSpy(player));
        } else {
            messages.send(invocation.source(), "player-only");
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityfriends.command.socialspy") || invocation.source().hasPermission("velocityfriends.admin.spy");
    }
}
