package com.velofriends.velocityfriends.command;

import com.velocitypowered.api.command.CommandSource;
import com.velofriends.velocityfriends.config.MessageManager;
import com.velofriends.velocityfriends.util.OperationResult;

import java.util.concurrent.CompletableFuture;

final class CommandUtil {
    private CommandUtil() {
    }

    static void sendResult(CommandSource source, MessageManager messages, CompletableFuture<OperationResult> future) {
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                messages.send(source, "storage-error");
                return;
            }
            if (!result.messageKey().isBlank()) {
                messages.send(source, result.messageKey(), result.placeholders());
            }
        });
    }

    static String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    static int page(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(args[index]));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
