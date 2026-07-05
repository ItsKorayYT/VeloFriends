package com.velofriends.velocityfriends.storage;

import com.velofriends.velocityfriends.config.PluginConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JdbcDatabase implements Database {
    private final Path dataDirectory;
    private final PluginConfig config;
    private final Logger logger;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "VelocityFriends-Storage");
        thread.setDaemon(true);
        return thread;
    });
    private HikariDataSource dataSource;
    private String dialect;

    public JdbcDatabase(Path dataDirectory, PluginConfig config, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dataDirectory);
                setupDataSource();
                runSchema();
            } catch (SQLException | IOException exception) {
                throw new StorageException("Unable to initialize storage", exception);
            }
        }, executor);
    }

    @Override
    public <T> CompletableFuture<T> query(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return function.apply(connection);
            } catch (SQLException exception) {
                throw new StorageException("Storage operation failed", exception);
            }
        }, executor);
    }

    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public String dialect() {
        return dialect;
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
        executor.shutdown();
    }

    private void setupDataSource() throws IOException {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("VelocityFriends");
        if (config.storage().mysqlEnabled()) {
            PluginConfig.MySql mysql = config.storage().mysql();
            dialect = "mysql";
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setJdbcUrl("jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database()
                    + "?useSSL=" + mysql.useSsl()
                    + "&characterEncoding=utf8"
                    + "&useUnicode=true"
                    + "&serverTimezone=UTC");
            hikari.setUsername(mysql.username());
            hikari.setPassword(mysql.password());
            hikari.setMaximumPoolSize(Math.max(1, mysql.poolSize()));
            hikari.setConnectionTimeout(mysql.connectionTimeoutMs());
        } else {
            dialect = "sqlite";
            Path sqlitePath = dataDirectory.resolve(config.storage().sqliteFile()).toAbsolutePath();
            Files.createDirectories(sqlitePath.getParent());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
            hikari.setMaximumPoolSize(1);
            hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");
        }
        dataSource = new HikariDataSource(hikari);
        logger.info("VelocityFriends using {} storage", dialect);
    }

    private void runSchema() throws SQLException, IOException {
        String resource = "schema/" + dialect + ".sql";
        String sql = readResource(resource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String command : Arrays.stream(sql.split(";")).map(String::trim).filter(s -> !s.isBlank()).toList()) {
                statement.execute(command);
            }
        }
    }

    private String readResource(String resource) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing bundled schema " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return String.join("\n", reader.lines().filter(Objects::nonNull).toList());
            }
        }
    }
}
