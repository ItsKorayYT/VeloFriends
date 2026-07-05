package com.velofriends.velocityfriends.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigManager {
    private final Path dataDirectory;
    private final Logger logger;
    private final Yaml yaml = new Yaml();
    private PluginConfig config;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    public void load() {
        try {
            Files.createDirectories(dataDirectory);
            Path path = dataDirectory.resolve("config.yml");
            copyDefault(path, "config.yml");
            try (Reader reader = Files.newBufferedReader(path)) {
                Object loaded = yaml.load(reader);
                if (loaded instanceof Map<?, ?> raw) {
                    config = PluginConfig.from(cast(raw));
                } else {
                    config = PluginConfig.from(Map.of());
                }
            }
        } catch (IOException exception) {
            logger.error("Unable to load VelocityFriends config.yml", exception);
            config = PluginConfig.from(Map.of());
        }
    }

    public PluginConfig config() {
        if (config == null) {
            load();
        }
        return config;
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

    private static Map<String, Object> cast(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
