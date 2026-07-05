package com.velofriends.velocityfriends.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PlayerNames {
    private static final Pattern JAVA_NAME = Pattern.compile("^[A-Za-z0-9_]{2,16}$");
    private static final Pattern BEDROCK_NAME = Pattern.compile("^\\.?[A-Za-z0-9_ ]{2,32}$");

    private PlayerNames() {
    }

    public static boolean valid(String input) {
        if (input == null) {
            return false;
        }
        String name = input.trim();
        return JAVA_NAME.matcher(name).matches() || BEDROCK_NAME.matcher(name).matches();
    }

    public static String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT);
    }
}
