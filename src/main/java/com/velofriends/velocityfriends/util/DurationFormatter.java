package com.velofriends.velocityfriends.util;

import java.time.Duration;

public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String compact(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
