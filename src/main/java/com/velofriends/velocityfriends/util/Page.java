package com.velofriends.velocityfriends.util;

import java.util.List;

public record Page<T>(List<T> items, int page, int maxPage, int totalItems) {
    public static <T> Page<T> of(List<T> items, int requestedPage, int pageSize) {
        int safePageSize = Math.max(1, pageSize);
        int maxPage = Math.max(1, (int) Math.ceil(items.size() / (double) safePageSize));
        int page = Math.min(Math.max(1, requestedPage), maxPage);
        int from = Math.min(items.size(), (page - 1) * safePageSize);
        int to = Math.min(items.size(), from + safePageSize);
        return new Page<>(List.copyOf(items.subList(from, to)), page, maxPage, items.size());
    }
}
