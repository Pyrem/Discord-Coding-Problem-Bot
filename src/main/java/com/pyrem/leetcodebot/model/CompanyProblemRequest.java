package com.pyrem.leetcodebot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Data Transfer Object representing a parsed user request for LeetCode problems
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProblemRequest {

    /**
     * List of company names requested (e.g., ["Microsoft", "Google"])
     */
    private List<String> companies;

    /**
     * Requested time range (may be null if user didn't specify)
     */
    private TimeRange timeRange;

    /**
     * Whether the user explicitly specified a time range
     * If false, we should use the automatic time range selection logic
     */
    private boolean explicitTimeRange;

    /**
     * Normalize company name for database table naming
     * Converts to lowercase and removes special characters
     */
    public static String normalizeCompanyName(String company) {
        if (company == null) {
            return null;
        }
        return company.toLowerCase()
            .replaceAll("[^a-z0-9]", "")
            .trim();
    }

    /**
     * Get table name for a specific company and time range
     * Format: {normalized_company}_{time_range_key}
     * Example: "microsoft_last30days"
     */
    public static String getTableName(String company, TimeRange timeRange) {
        String normalizedCompany = normalizeCompanyName(company);
        return normalizedCompany + "_" + timeRange.getTableSuffix();
    }
}
