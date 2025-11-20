package com.pyrem.leetcodebot.model;

/**
 * Enumeration representing LeetCode problem difficulty levels
 */
public enum ProblemDifficulty {
    EASY("Easy"),
    MEDIUM("Med."),
    HARD("Hard");

    private final String displayName;

    ProblemDifficulty(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parse difficulty from string (case-insensitive)
     */
    public static ProblemDifficulty fromString(String difficulty) {
        if (difficulty == null) {
            return MEDIUM; // Default
        }

        String normalized = difficulty.toLowerCase().trim();
        return switch (normalized) {
            case "easy" -> EASY;
            case "medium", "med", "med." -> MEDIUM;
            case "hard" -> HARD;
            default -> MEDIUM;
        };
    }
}
