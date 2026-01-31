package com.pyrem.leetcodebot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents cache metadata for a problem set stored in DynamoDB.
 * Used to track when data was last updated and problem counts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedProblemSet {

    /**
     * Normalized company name (lowercase, no special chars)
     */
    private String companyName;

    /**
     * Time range key (e.g., "last30days", "last3months")
     */
    private String timeRange;

    /**
     * Number of problems cached
     */
    private Integer problemCount;

    /**
     * When the cache was last updated
     */
    private LocalDateTime lastUpdated;

    /**
     * When the cache entry was created
     */
    private LocalDateTime createdAt;

    /**
     * Check if the cache is expired based on expiry days
     */
    public boolean isExpired(int expiryDays) {
        if (lastUpdated == null) {
            return true;
        }
        return lastUpdated.plusDays(expiryDays).isBefore(LocalDateTime.now());
    }

    /**
     * Get the composite key for this cache entry
     */
    public String getCacheKey() {
        return companyName + "_" + timeRange;
    }
}
