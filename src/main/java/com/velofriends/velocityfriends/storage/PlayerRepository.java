package com.velofriends.velocityfriends.storage;

import com.velofriends.velocityfriends.model.PlayerProfile;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerRepository {
    private final Database database;
    private final Map<UUID, PlayerProfile> byUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();

    public PlayerRepository(Database database) {
        this.database = database;
    }

    public CompletableFuture<Void> upsert(UUID uuid, String username) {
        long now = Instant.now().toEpochMilli();
        PlayerProfile profile = new PlayerProfile(uuid, username, now, now);
        cache(profile);
        return database.execute(connection -> {
            String sql = database.dialect().equals("mysql")
                    ? "INSERT INTO players (uuid, username, username_lower, first_seen, last_seen) VALUES (?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE username=VALUES(username), username_lower=VALUES(username_lower), last_seen=VALUES(last_seen)"
                    : "INSERT INTO players (uuid, username, username_lower, first_seen, last_seen) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET username=excluded.username, username_lower=excluded.username_lower, last_seen=excluded.last_seen";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, username);
                statement.setString(3, username.toLowerCase(Locale.ROOT));
                statement.setLong(4, now);
                statement.setLong(5, now);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> touchLastSeen(UUID uuid) {
        long now = Instant.now().toEpochMilli();
        PlayerProfile cached = byUuid.get(uuid);
        if (cached != null) {
            cache(new PlayerProfile(uuid, cached.username(), cached.firstSeen(), now));
        }
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE players SET last_seen=? WHERE uuid=?")) {
                statement.setLong(1, now);
                statement.setString(2, uuid.toString());
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Optional<PlayerProfile>> findByUuid(UUID uuid) {
        PlayerProfile cached = byUuid.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM players WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        PlayerProfile profile = readProfile(result);
                        cache(profile);
                        return Optional.of(profile);
                    }
                }
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Optional<PlayerProfile>> findByName(String username) {
        String normalized = username.toLowerCase(Locale.ROOT);
        UUID cachedUuid = byName.get(normalized);
        if (cachedUuid != null) {
            PlayerProfile cached = byUuid.get(cachedUuid);
            if (cached != null) {
                return CompletableFuture.completedFuture(Optional.of(cached));
            }
        }
        return database.query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM players WHERE username_lower=? ORDER BY last_seen DESC LIMIT 1")) {
                statement.setString(1, normalized);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        PlayerProfile profile = readProfile(result);
                        cache(profile);
                        return Optional.of(profile);
                    }
                }
            }
            return Optional.empty();
        });
    }

    private void cache(PlayerProfile profile) {
        byUuid.put(profile.uuid(), profile);
        byName.put(profile.usernameLower(), profile.uuid());
    }

    private static PlayerProfile readProfile(ResultSet result) throws SQLException {
        return new PlayerProfile(
                UUID.fromString(result.getString("uuid")),
                result.getString("username"),
                result.getLong("first_seen"),
                result.getLong("last_seen")
        );
    }
}
