package com.velofriends.velocityfriends.config;

import com.velofriends.velocityfriends.model.PrivacyLevel;
import com.velofriends.velocityfriends.model.ServerVisibility;

import java.util.LinkedHashMap;
import java.util.Map;

public record PluginConfig(
        Storage storage,
        Friends friends,
        PrivacyDefaults privacyDefaults,
        Notifications notifications,
        MessageFormats messageFormats,
        Gui gui,
        boolean debug
) {
    @SuppressWarnings("unchecked")
    public static PluginConfig from(Map<String, Object> root) {
        Map<String, Object> storage = map(root, "storage");
        Map<String, Object> mysql = map(storage, "mysql");
        Map<String, Object> friends = map(root, "friends");
        Map<String, Object> privacy = map(root, "privacy-defaults");
        Map<String, Object> notifications = map(root, "notifications");
        Map<String, Object> messages = map(root, "messages");
        Map<String, Object> format = map(messages, "format");
        Map<String, Object> gui = map(root, "gui");
        Map<String, Object> titles = map(gui, "titles");
        Map<String, Object> buttons = map(gui, "buttons");

        Map<String, Integer> permissionLimits = new LinkedHashMap<>();
        Object rawLimits = friends.get("permission-limits");
        if (rawLimits instanceof Map<?, ?> rawMap) {
            rawMap.forEach((key, value) -> permissionLimits.put(String.valueOf(key), intValue(value, 0)));
        }

        return new PluginConfig(
                new Storage(
                        string(storage, "type", "sqlite"),
                        string(storage, "sqlite-file", "velocityfriends.db"),
                        new MySql(
                                string(mysql, "host", "localhost"),
                                intValue(mysql.get("port"), 3306),
                                string(mysql, "database", "velocityfriends"),
                                string(mysql, "username", "velocityfriends"),
                                string(mysql, "password", "change-me"),
                                bool(mysql, "use-ssl", false),
                                intValue(mysql.get("pool-size"), 10),
                                longValue(mysql.get("connection-timeout-ms"), 10000L)
                        )
                ),
                new Friends(
                        longValue(friends.get("request-expiry-minutes"), 10080L),
                        longValue(friends.get("request-cooldown-seconds"), 30L),
                        intValue(friends.get("default-limit"), 100),
                        permissionLimits,
                        intValue(friends.get("purge-batch-size"), 500)
                ),
                new PrivacyDefaults(
                        bool(privacy, "friend-requests", true),
                        PrivacyLevel.fromConfig(string(privacy, "dm-privacy", "everyone"), PrivacyLevel.EVERYONE),
                        PrivacyLevel.fromConfig(string(privacy, "online-status-visibility", "friends"), PrivacyLevel.FRIENDS_ONLY),
                        ServerVisibility.fromConfig(string(privacy, "server-visibility", "friends"), ServerVisibility.FRIENDS),
                        bool(privacy, "messages-enabled", true),
                        bool(privacy, "friend-notifications", true),
                        bool(privacy, "social-spy", false),
                        bool(privacy, "actionbar-dm", false),
                        bool(privacy, "sound-dm", false)
                ),
                new Notifications(
                        bool(notifications, "friend-join", true),
                        bool(notifications, "friend-leave", true),
                        bool(notifications, "friend-server-change", true)
                ),
                new MessageFormats(
                        string(format, "incoming-dm", "<gray>[<aqua>{player}</aqua> -> you]</gray> <white>{message}</white>"),
                        string(format, "outgoing-dm", "<gray>[you -> <aqua>{target}</aqua>]</gray> <white>{message}</white>"),
                        string(format, "spy-dm", "<dark_gray>[Spy]</dark_gray> <gray>{player} -> {target}: {message}</gray>"),
                        string(format, "join", "<green>{player}</green> <gray>joined the network.</gray>"),
                        string(format, "leave", "<red>{player}</red> <gray>left the network.</gray>"),
                        string(format, "server-change", "<aqua>{player}</aqua> <gray>moved to</gray> <yellow>{server}</yellow><gray>.</gray>")
                ),
                new Gui(
                        bool(gui, "bedrock-forms-enabled", true),
                        bool(gui, "java-chat-menu-enabled", bool(gui, "java-chest-enabled", bool(gui, "java-clickable-enabled", true))),
                        bool(gui, "icons-enabled", false),
                        intValue(gui.get("page-size"), 8),
                        new Titles(
                                string(titles, "main", "Social Menu"),
                                string(titles, "friends", "Friends"),
                                string(titles, "requests", "Friend Requests"),
                                string(titles, "direct-message", "Direct Message"),
                                string(titles, "privacy", "Privacy Settings"),
                                string(titles, "blocked", "Blocked and Ignored"),
                                string(titles, "favorites", "Favorites")
                        ),
                        new Buttons(
                                string(buttons, "add-friend", "Add Friend"),
                                string(buttons, "friends", "Friends"),
                                string(buttons, "requests", "Requests"),
                                string(buttons, "direct-message", "Direct Message"),
                                string(buttons, "privacy", "Privacy Settings"),
                                string(buttons, "blocked", "Blocked/Ignored"),
                                string(buttons, "favorites", "Favorites"),
                                string(buttons, "back", "Back"),
                                string(buttons, "close", "Close")
                        )
                ),
                bool(root, "debug", false)
        );
    }

    public record Storage(String type, String sqliteFile, MySql mysql) {
        public boolean mysqlEnabled() {
            return "mysql".equalsIgnoreCase(type) || "mariadb".equalsIgnoreCase(type);
        }
    }

    public record MySql(
            String host,
            int port,
            String database,
            String username,
            String password,
            boolean useSsl,
            int poolSize,
            long connectionTimeoutMs
    ) {
    }

    public record Friends(
            long requestExpiryMinutes,
            long requestCooldownSeconds,
            int defaultLimit,
            Map<String, Integer> permissionLimits,
            int purgeBatchSize
    ) {
    }

    public record PrivacyDefaults(
            boolean friendRequests,
            PrivacyLevel dmPrivacy,
            PrivacyLevel onlineStatusVisibility,
            ServerVisibility serverVisibility,
            boolean messagesEnabled,
            boolean friendNotifications,
            boolean socialSpy,
            boolean actionbarDm,
            boolean soundDm
    ) {
    }

    public record Notifications(boolean friendJoin, boolean friendLeave, boolean friendServerChange) {
    }

    public record MessageFormats(
            String incomingDm,
            String outgoingDm,
            String spyDm,
            String join,
            String leave,
            String serverChange
    ) {
    }

    public record Gui(
            boolean bedrockFormsEnabled,
            boolean javaChatMenuEnabled,
            boolean iconsEnabled,
            int pageSize,
            Titles titles,
            Buttons buttons
    ) {
    }

    public record Titles(
            String main,
            String friends,
            String requests,
            String directMessage,
            String privacy,
            String blocked,
            String favorites
    ) {
    }

    public record Buttons(
            String addFriend,
            String friends,
            String requests,
            String directMessage,
            String privacy,
            String blocked,
            String favorites,
            String back,
            String close
    ) {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private static String string(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> source, String key, boolean fallback) {
        Object value = source.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
