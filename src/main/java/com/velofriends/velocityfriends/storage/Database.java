package com.velofriends.velocityfriends.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface Database extends AutoCloseable {
    CompletableFuture<Void> initialize();

    <T> CompletableFuture<T> query(SqlFunction<T> function);

    default CompletableFuture<Void> execute(SqlConsumer consumer) {
        return query(connection -> {
            consumer.accept(connection);
            return null;
        });
    }

    Executor executor();

    String dialect();

    @FunctionalInterface
    interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
