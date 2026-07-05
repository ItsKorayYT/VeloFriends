package com.velofriends.velocityfriends.storage;

import com.velofriends.velocityfriends.config.PluginConfig;
import com.velofriends.velocityfriends.model.FriendRequest;
import com.velofriends.velocityfriends.model.PlayerSettings;
import com.velofriends.velocityfriends.model.PrivacyLevel;
import com.velofriends.velocityfriends.model.ServerVisibility;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SocialRepository {
    private final Database database;
    private final PluginConfig.PrivacyDefaults defaults;
    private final Map<UUID, PlayerSettings> settingsCache = new ConcurrentHashMap<>();

    public SocialRepository(Database database, PluginConfig.PrivacyDefaults defaults) {
        this.database = database;
        this.defaults = defaults;
    }

    public CompletableFuture<PlayerSettings> settings(UUID uuid) {
        PlayerSettings cached = settingsCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM settings WHERE player_uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        PlayerSettings settings = readSettings(result);
                        settingsCache.put(uuid, settings);
                        return settings;
                    }
                }
            }
            PlayerSettings settings = defaultSettings(uuid);
            saveSettings(connection, settings);
            settingsCache.put(uuid, settings);
            return settings;
        });
    }

    public CompletableFuture<Void> saveSettings(PlayerSettings settings) {
        settingsCache.put(settings.playerUuid(), settings);
        return database.execute(connection -> saveSettings(connection, settings));
    }

    public CompletableFuture<Boolean> areFriends(UUID player, UUID target) {
        return exists("SELECT 1 FROM friends WHERE player_uuid=? AND friend_uuid=?", player, target);
    }

    public CompletableFuture<Integer> friendCount(UUID player) {
        return count("SELECT COUNT(*) FROM friends WHERE player_uuid=?", player);
    }

    public CompletableFuture<List<UUID>> friends(UUID player) {
        return uuidList("SELECT friend_uuid FROM friends WHERE player_uuid=? ORDER BY favorite DESC, created_at ASC", player);
    }

    public CompletableFuture<List<UUID>> favorites(UUID player) {
        return uuidList("SELECT friend_uuid FROM friends WHERE player_uuid=? AND favorite=1 ORDER BY created_at ASC", player);
    }

    public CompletableFuture<Void> addFriendship(UUID first, UUID second) {
        long now = Instant.now().toEpochMilli();
        return database.execute(connection -> {
            String insert = insertIgnore("friends", "player_uuid, friend_uuid, created_at, favorite", "?, ?, ?, 0");
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, first.toString());
                statement.setString(2, second.toString());
                statement.setLong(3, now);
                statement.executeUpdate();
                statement.setString(1, second.toString());
                statement.setString(2, first.toString());
                statement.setLong(3, now);
                statement.executeUpdate();
            }
            deleteRequest(connection, first, second);
            deleteRequest(connection, second, first);
        });
    }

    public CompletableFuture<Void> removeFriendship(UUID first, UUID second) {
        return database.execute(connection -> {
            deletePair(connection, "friends", "player_uuid", "friend_uuid", first, second);
            deletePair(connection, "friends", "player_uuid", "friend_uuid", second, first);
            deletePair(connection, "notes", "player_uuid", "friend_uuid", first, second);
            deletePair(connection, "notes", "player_uuid", "friend_uuid", second, first);
        });
    }

    public CompletableFuture<Boolean> toggleFavorite(UUID player, UUID friend) {
        return database.query(connection -> {
            boolean current = exists(connection, "SELECT 1 FROM friends WHERE player_uuid=? AND friend_uuid=? AND favorite=1", player, friend);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE friends SET favorite=? WHERE player_uuid=? AND friend_uuid=?")) {
                statement.setInt(1, current ? 0 : 1);
                statement.setString(2, player.toString());
                statement.setString(3, friend.toString());
                statement.executeUpdate();
            }
            return !current;
        });
    }

    public CompletableFuture<Void> setNote(UUID player, UUID friend, String note) {
        long now = Instant.now().toEpochMilli();
        return database.execute(connection -> {
            String sql = database.dialect().equals("mysql")
                    ? "INSERT INTO notes (player_uuid, friend_uuid, note, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE note=VALUES(note), updated_at=VALUES(updated_at)"
                    : "INSERT INTO notes (player_uuid, friend_uuid, note, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT(player_uuid, friend_uuid) DO UPDATE SET note=excluded.note, updated_at=excluded.updated_at";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, player.toString());
                statement.setString(2, friend.toString());
                statement.setString(3, note);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<String>> note(UUID player, UUID friend) {
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT note FROM notes WHERE player_uuid=? AND friend_uuid=?")) {
                statement.setString(1, player.toString());
                statement.setString(2, friend.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(result.getString("note"));
                    }
                }
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Optional<FriendRequest>> request(UUID requester, UUID target) {
        return database.query(connection -> request(connection, requester, target));
    }

    public CompletableFuture<Void> createRequest(UUID requester, UUID target, long expiresAt) {
        long now = Instant.now().toEpochMilli();
        return database.execute(connection -> {
            String sql = insertIgnore("friend_requests", "requester_uuid, target_uuid, created_at, expires_at", "?, ?, ?, ?");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, requester.toString());
                statement.setString(2, target.toString());
                statement.setLong(3, now);
                statement.setLong(4, expiresAt);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> deleteRequest(UUID requester, UUID target) {
        return database.execute(connection -> deleteRequest(connection, requester, target));
    }

    public CompletableFuture<List<FriendRequest>> incomingRequests(UUID target) {
        return requests("SELECT * FROM friend_requests WHERE target_uuid=? ORDER BY created_at ASC", target);
    }

    public CompletableFuture<List<FriendRequest>> outgoingRequests(UUID requester) {
        return requests("SELECT * FROM friend_requests WHERE requester_uuid=? ORDER BY created_at ASC", requester);
    }

    public CompletableFuture<Integer> deleteExpiredRequests() {
        long now = Instant.now().toEpochMilli();
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM friend_requests WHERE expires_at <= ?")) {
                statement.setLong(1, now);
                return statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> isBlocked(UUID player, UUID target) {
        return exists("SELECT 1 FROM blocks WHERE player_uuid=? AND blocked_uuid=?", player, target);
    }

    public CompletableFuture<Void> block(UUID player, UUID target) {
        long now = Instant.now().toEpochMilli();
        return database.execute(connection -> {
            String sql = insertIgnore("blocks", "player_uuid, blocked_uuid, created_at", "?, ?, ?");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, player.toString());
                statement.setString(2, target.toString());
                statement.setLong(3, now);
                statement.executeUpdate();
            }
            deletePair(connection, "friends", "player_uuid", "friend_uuid", player, target);
            deletePair(connection, "friends", "player_uuid", "friend_uuid", target, player);
            deleteRequest(connection, player, target);
            deleteRequest(connection, target, player);
        });
    }

    public CompletableFuture<Void> unblock(UUID player, UUID target) {
        return database.execute(connection -> deletePair(connection, "blocks", "player_uuid", "blocked_uuid", player, target));
    }

    public CompletableFuture<List<UUID>> blocks(UUID player) {
        return uuidList("SELECT blocked_uuid FROM blocks WHERE player_uuid=? ORDER BY created_at ASC", player);
    }

    public CompletableFuture<Boolean> isIgnored(UUID player, UUID target) {
        return exists("SELECT 1 FROM ignores WHERE player_uuid=? AND ignored_uuid=?", player, target);
    }

    public CompletableFuture<Void> ignore(UUID player, UUID target) {
        long now = Instant.now().toEpochMilli();
        return database.execute(connection -> {
            String sql = insertIgnore("ignores", "player_uuid, ignored_uuid, created_at", "?, ?, ?");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, player.toString());
                statement.setString(2, target.toString());
                statement.setLong(3, now);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> unignore(UUID player, UUID target) {
        return database.execute(connection -> deletePair(connection, "ignores", "player_uuid", "ignored_uuid", player, target));
    }

    public CompletableFuture<List<UUID>> ignores(UUID player) {
        return uuidList("SELECT ignored_uuid FROM ignores WHERE player_uuid=? ORDER BY created_at ASC", player);
    }

    public CompletableFuture<PurgeResult> purgePlayersOlderThan(long cutoffMillis, int batchSize) {
        int safeBatch = Math.max(1, batchSize);
        return database.query(connection -> {
            List<UUID> uuids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid FROM players WHERE last_seen < ? ORDER BY last_seen ASC LIMIT ?")) {
                statement.setLong(1, cutoffMillis);
                statement.setInt(2, safeBatch);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        uuids.add(UUID.fromString(result.getString("uuid")));
                    }
                }
            }
            for (UUID uuid : uuids) {
                deletePlayerSocialRows(connection, uuid);
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM players WHERE uuid=?")) {
                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();
                }
                settingsCache.remove(uuid);
            }
            return new PurgeResult(uuids.size());
        });
    }

    public CompletableFuture<DebugSnapshot> debug(UUID player) {
        return database.query(connection -> new DebugSnapshot(
                count(connection, "SELECT COUNT(*) FROM friends WHERE player_uuid=?", player),
                count(connection, "SELECT COUNT(*) FROM friend_requests WHERE requester_uuid=? OR target_uuid=?", player, player),
                count(connection, "SELECT COUNT(*) FROM ignores WHERE player_uuid=?", player),
                count(connection, "SELECT COUNT(*) FROM blocks WHERE player_uuid=?", player),
                settingsCache.containsKey(player) ? "cached" : "database"
        ));
    }

    private PlayerSettings defaultSettings(UUID uuid) {
        return new PlayerSettings(uuid, defaults.friendRequests(), defaults.dmPrivacy(),
                defaults.onlineStatusVisibility(), defaults.serverVisibility(), defaults.messagesEnabled(),
                defaults.socialSpy(), defaults.friendNotifications(), defaults.actionbarDm(), defaults.soundDm());
    }


    private void saveSettings(java.sql.Connection connection, PlayerSettings settings) throws SQLException {
        String sql = database.dialect().equals("mysql")
                ? "INSERT INTO settings (player_uuid, friend_requests_enabled, dm_privacy, online_status_visibility, server_visibility, messages_enabled, social_spy, friend_notifications, actionbar_dm, sound_dm) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "friend_requests_enabled=VALUES(friend_requests_enabled), dm_privacy=VALUES(dm_privacy), online_status_visibility=VALUES(online_status_visibility), "
                + "server_visibility=VALUES(server_visibility), messages_enabled=VALUES(messages_enabled), social_spy=VALUES(social_spy), "
                + "friend_notifications=VALUES(friend_notifications), actionbar_dm=VALUES(actionbar_dm), sound_dm=VALUES(sound_dm)"
                : "INSERT INTO settings (player_uuid, friend_requests_enabled, dm_privacy, online_status_visibility, server_visibility, messages_enabled, social_spy, friend_notifications, actionbar_dm, sound_dm) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET "
                + "friend_requests_enabled=excluded.friend_requests_enabled, dm_privacy=excluded.dm_privacy, online_status_visibility=excluded.online_status_visibility, "
                + "server_visibility=excluded.server_visibility, messages_enabled=excluded.messages_enabled, social_spy=excluded.social_spy, "
                + "friend_notifications=excluded.friend_notifications, actionbar_dm=excluded.actionbar_dm, sound_dm=excluded.sound_dm";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, settings.playerUuid().toString());
            statement.setInt(2, settings.friendRequestsEnabled() ? 1 : 0);
            statement.setString(3, settings.dmPrivacy().name());
            statement.setString(4, settings.onlineStatusVisibility().name());
            statement.setString(5, settings.serverVisibility().name());
            statement.setInt(6, settings.messagesEnabled() ? 1 : 0);
            statement.setInt(7, settings.socialSpy() ? 1 : 0);
            statement.setInt(8, settings.friendNotifications() ? 1 : 0);
            statement.setInt(9, settings.actionbarDm() ? 1 : 0);
            statement.setInt(10, settings.soundDm() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    private PlayerSettings readSettings(ResultSet result) throws SQLException {
        UUID uuid = UUID.fromString(result.getString("player_uuid"));
        return new PlayerSettings(uuid,
                result.getInt("friend_requests_enabled") == 1,
                PrivacyLevel.valueOf(result.getString("dm_privacy")),
                PrivacyLevel.valueOf(result.getString("online_status_visibility")),
                ServerVisibility.valueOf(result.getString("server_visibility")),
                result.getInt("messages_enabled") == 1,
                result.getInt("social_spy") == 1,
                result.getInt("friend_notifications") == 1,
                result.getInt("actionbar_dm") == 1,
                result.getInt("sound_dm") == 1);
    }

    private CompletableFuture<Boolean> exists(String sql, UUID first, UUID second) {
        return database.query(connection -> exists(connection, sql, first, second));
    }

    private boolean exists(java.sql.Connection connection, String sql, UUID first, UUID second) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, first.toString());
            statement.setString(2, second.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private CompletableFuture<Integer> count(String sql, UUID player) {
        return database.query(connection -> count(connection, sql, player));
    }

    private int count(java.sql.Connection connection, String sql, UUID... players) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < players.length; i++) {
                statement.setString(i + 1, players[i].toString());
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private CompletableFuture<List<UUID>> uuidList(String sql, UUID player) {
        return database.query(connection -> {
            List<UUID> uuids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, player.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        uuids.add(UUID.fromString(result.getString(1)));
                    }
                }
            }
            return uuids;
        });
    }

    private CompletableFuture<List<FriendRequest>> requests(String sql, UUID player) {
        return database.query(connection -> {
            List<FriendRequest> requests = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, player.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        requests.add(readRequest(result));
                    }
                }
            }
            return requests;
        });
    }

    private Optional<FriendRequest> request(java.sql.Connection connection, UUID requester, UUID target) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM friend_requests WHERE requester_uuid=? AND target_uuid=?")) {
            statement.setString(1, requester.toString());
            statement.setString(2, target.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(readRequest(result));
                }
            }
        }
        return Optional.empty();
    }

    private FriendRequest readRequest(ResultSet result) throws SQLException {
        return new FriendRequest(
                UUID.fromString(result.getString("requester_uuid")),
                UUID.fromString(result.getString("target_uuid")),
                result.getLong("created_at"),
                result.getLong("expires_at")
        );
    }

    private void deleteRequest(java.sql.Connection connection, UUID requester, UUID target) throws SQLException {
        deletePair(connection, "friend_requests", "requester_uuid", "target_uuid", requester, target);
    }

    private void deletePair(java.sql.Connection connection, String table, String firstColumn, String secondColumn,
                            UUID first, UUID second) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE " + firstColumn + "=? AND " + secondColumn + "=?")) {
            statement.setString(1, first.toString());
            statement.setString(2, second.toString());
            statement.executeUpdate();
        }
    }

    private void deletePlayerSocialRows(java.sql.Connection connection, UUID uuid) throws SQLException {
        String id = uuid.toString();
        String[] deletes = {
                "DELETE FROM friends WHERE player_uuid=? OR friend_uuid=?",
                "DELETE FROM friend_requests WHERE requester_uuid=? OR target_uuid=?",
                "DELETE FROM blocks WHERE player_uuid=? OR blocked_uuid=?",
                "DELETE FROM ignores WHERE player_uuid=? OR ignored_uuid=?",
                "DELETE FROM settings WHERE player_uuid=?",
                "DELETE FROM notes WHERE player_uuid=? OR friend_uuid=?"
        };
        for (String sql : deletes) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                if (sql.indexOf('?') != sql.lastIndexOf('?')) {
                    statement.setString(2, id);
                }
                statement.executeUpdate();
            }
        }
    }

    private String insertIgnore(String table, String columns, String values) {
        if (database.dialect().equals("mysql")) {
            return "INSERT IGNORE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
        }
        return "INSERT OR IGNORE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
    }

    public record PurgeResult(int purged) {
    }

    public record DebugSnapshot(int friends, int requests, int ignored, int blocked, String settingsState) {
    }
}
