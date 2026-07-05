package com.velofriends.velocityfriends.config;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageManager {
    private final Path dataDirectory;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Yaml yaml = new Yaml();
    private Map<String, String> messages = Map.of();

    public MessageManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void load() {
        try {
            Files.createDirectories(dataDirectory);
            Path path = dataDirectory.resolve("messages.yml");
            copyDefault(path, "messages.yml");
            Map<String, String> parsed = new LinkedHashMap<>(loadDefaults());
            try (Reader reader = Files.newBufferedReader(path)) {
                Object loaded = yaml.load(reader);
                if (loaded instanceof Map<?, ?> raw) {
                    raw.forEach((key, value) -> parsed.put(String.valueOf(key), String.valueOf(value)));
                }
            }
            messages = parsed;
        } catch (IOException exception) {
            logger.error("Unable to load VelocityFriends messages.yml", exception);
            messages = Map.of();
        }
    }

    public void send(CommandSource source, String key) {
        send(source, key, Map.of());
    }

    public void send(CommandSource source, String key, Map<String, String> placeholders) {
        source.sendMessage(prefixed(key, placeholders));
    }

    public Component prefixed(String key, Map<String, String> placeholders) {
        return deserialize(raw("prefix", "") + raw(key, "<red>Missing message: " + key + "</red>"), placeholders);
    }

    public Component component(String key, Map<String, String> placeholders) {
        return deserialize(raw(key, "<red>Missing message: " + key + "</red>"), placeholders);
    }

    public Component fromFormat(String format, Map<String, String> placeholders) {
        return deserialize(format, placeholders);
    }

    public String raw(String key, String fallback) {
        return messages.getOrDefault(key, fallback);
    }

    private Component deserialize(String miniMessageText, Map<String, String> placeholders) {
        String rendered = miniMessageText;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", escapeMiniMessage(entry.getValue()));
        }
        return miniMessage.deserialize(rendered);
    }

    private static String escapeMiniMessage(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    private void copyDefault(Path target, String resourceName) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Missing bundled resource " + resourceName);
            }
            Files.copy(input, target);
        }
    }

    private Map<String, String> loadDefaults() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("messages.yml")) {
            if (input == null) {
                throw new IOException("Missing bundled resource messages.yml");
            }
            Object loaded = yaml.load(input);
            Map<String, String> parsed = new LinkedHashMap<>();
            if (loaded instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> parsed.put(String.valueOf(key), String.valueOf(value)));
            }
            return parsed;
        }
    }
}
