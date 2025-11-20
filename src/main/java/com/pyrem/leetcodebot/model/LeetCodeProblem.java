package com.pyrem.leetcodebot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a LeetCode problem with all relevant metadata.
 * This class is used for both database storage and data transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeetCodeProblem {

    /**
     * Unique LeetCode problem number (e.g., 1, 2, 394)
     */
    private Integer problemNumber;

    /**
     * Problem title (e.g., "Two Sum", "Add Two Numbers")
     */
    private String problemName;

    /**
     * Acceptance rate as a decimal (e.g., 0.566 for 56.6%)
     */
    private Double acceptanceRate;

    /**
     * Problem difficulty level
     */
    private ProblemDifficulty difficulty;

    /**
     * Frequency of the problem being asked (0.0 to 1.0)
     * Higher values indicate more frequently asked
     */
    private Double frequency;

    /**
     * Direct URL to the problem on LeetCode
     */
    private String url;

    /**
     * Generate LeetCode URL from problem number if not provided
     */
    public String getUrl() {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        // Generate URL from problem name
        if (problemName != null) {
            String slug = problemName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
            return "https://leetcode.com/problems/" + slug + "/";
        }
        return null;
    }
}
