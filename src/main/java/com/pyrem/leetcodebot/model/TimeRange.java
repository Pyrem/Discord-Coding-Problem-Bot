package com.pyrem.leetcodebot.model;

/**
 * Enumeration representing LeetCode time ranges for problem frequency
 */
public enum TimeRange {
    LAST_30_DAYS("last30days", "Last 30 Days", 30),
    LAST_3_MONTHS("last3months", "Last 3 Months", 90),
    LAST_6_MONTHS("last6months", "Last 6 Months", 180),
    MORE_THAN_6_MONTHS("morethan6months", "More than 6 Months", 365),
    ALL("all", "All Time", Integer.MAX_VALUE);

    private final String key;
    private final String displayName;
    private final int days;

    TimeRange(String key, String displayName, int days) {
        this.key = key;
        this.displayName = displayName;
        this.days = days;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDays() {
        return days;
    }

    /**
     * Get table suffix for this time range (e.g., "last30days")
     */
    public String getTableSuffix() {
        return key;
    }

    /**
     * Parse time range from string (case-insensitive)
     */
    public static TimeRange fromString(String range) {
        if (range == null) {
            return LAST_30_DAYS; // Default
        }

        String normalized = range.toLowerCase()
            .replaceAll("[^a-z0-9]", "");

        // Check for numeric patterns (e.g., "30 days", "3 months")
        if (normalized.contains("30") && normalized.contains("day")) {
            return LAST_30_DAYS;
        } else if (normalized.contains("3") && normalized.contains("month")) {
            return LAST_3_MONTHS;
        } else if (normalized.contains("6") && normalized.contains("month")) {
            return LAST_6_MONTHS;
        } else if (normalized.contains("all")) {
            return ALL;
        }

        // Try exact key matching
        for (TimeRange tr : values()) {
            if (normalized.equals(tr.key) || normalized.equals(tr.key.replace("_", ""))) {
                return tr;
            }
        }

        return LAST_30_DAYS; // Default
    }

    /**
     * Get the next wider time range, or null if already at ALL
     */
    public TimeRange getNextWider() {
        return switch (this) {
            case LAST_30_DAYS -> LAST_3_MONTHS;
            case LAST_3_MONTHS -> LAST_6_MONTHS;
            case LAST_6_MONTHS -> MORE_THAN_6_MONTHS;
            case MORE_THAN_6_MONTHS -> ALL;
            case ALL -> null;
        };
    }
}
