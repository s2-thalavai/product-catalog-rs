package com.infy.catalog.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Typed application configuration, read once from environment variables at startup.
 * All validation happens here so a bad deployment fails fast with a clear message.
 */
public record AppConfig(
        int port,
        StoreType storeType,
        String dbUrl,
        String dbUsername,
        String dbPassword,
        int dbPoolSize,
        boolean seedData,
        int httpThreads,
        int defaultPageSize,
        int maxPageSize,
        int shutdownGraceSeconds,
        List<String> corsAllowedOrigins) {

    public enum StoreType { MEMORY, POSTGRES }

    public AppConfig {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("APP_PORT must be between 0 and 65535");
        }
        Objects.requireNonNull(storeType, "storeType");
        if (storeType == StoreType.POSTGRES && (isBlank(dbUrl) || isBlank(dbUsername) || dbPassword == null)) {
            throw new IllegalArgumentException("DB_URL, DB_USERNAME and DB_PASSWORD are required when STORE_TYPE=postgres");
        }
        if (dbPoolSize < 1) {
            throw new IllegalArgumentException("DB_POOL_SIZE must be >= 1");
        }
        if (httpThreads < 1) {
            throw new IllegalArgumentException("HTTP_THREADS must be >= 1");
        }
        if (defaultPageSize < 1) {
            throw new IllegalArgumentException("PAGE_SIZE_DEFAULT must be >= 1");
        }
        if (maxPageSize < defaultPageSize) {
            throw new IllegalArgumentException("PAGE_SIZE_MAX must be >= PAGE_SIZE_DEFAULT");
        }
        if (shutdownGraceSeconds < 0) {
            throw new IllegalArgumentException("SHUTDOWN_GRACE_SECONDS must be >= 0");
        }
        corsAllowedOrigins = List.copyOf(corsAllowedOrigins);
        for (String origin : corsAllowedOrigins) {
            if (origin.equals("*") || !(origin.startsWith("http://") || origin.startsWith("https://"))) {
                throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must list explicit origins like "
                        + "https://app.example.com (no '*'), but contained '" + origin + "'");
            }
        }
    }

    public static AppConfig fromEnvironment(Map<String, String> env) {
        return new AppConfig(
                intValue(env, "APP_PORT", 8080),
                storeType(stringValue(env, "STORE_TYPE", "memory")),
                stringValue(env, "DB_URL", null),
                stringValue(env, "DB_USERNAME", null),
                stringValue(env, "DB_PASSWORD", null),
                intValue(env, "DB_POOL_SIZE", 10),
                Boolean.parseBoolean(stringValue(env, "SEED_DATA", "false")),
                intValue(env, "HTTP_THREADS", 32),
                intValue(env, "PAGE_SIZE_DEFAULT", 20),
                intValue(env, "PAGE_SIZE_MAX", 100),
                intValue(env, "SHUTDOWN_GRACE_SECONDS", 10),
                listValue(env, "CORS_ALLOWED_ORIGINS"));
    }

    /** Never print the password. */
    @Override
    public String toString() {
        return "AppConfig[port=" + port
                + ", storeType=" + storeType
                + ", dbUrl=" + dbUrl
                + ", dbUsername=" + dbUsername
                + ", dbPassword=" + (dbPassword == null ? "null" : "****")
                + ", dbPoolSize=" + dbPoolSize
                + ", seedData=" + seedData
                + ", httpThreads=" + httpThreads
                + ", defaultPageSize=" + defaultPageSize
                + ", maxPageSize=" + maxPageSize
                + ", shutdownGraceSeconds=" + shutdownGraceSeconds
                + ", corsAllowedOrigins=" + corsAllowedOrigins + "]";
    }

    private static StoreType storeType(String raw) {
        try {
            return StoreType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("STORE_TYPE must be 'memory' or 'postgres' but was '" + raw + "'", e);
        }
    }

    private static String stringValue(Map<String, String> env, String key, String defaultValue) {
        String value = env.get(key);
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static int intValue(Map<String, String> env, String key, int defaultValue) {
        String value = stringValue(env, key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer but was '" + value + "'", e);
        }
    }

    private static List<String> listValue(Map<String, String> env, String key) {
        String value = stringValue(env, key, null);
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
