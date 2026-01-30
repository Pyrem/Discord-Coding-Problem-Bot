package com.pyrem.leetcodebot.lambda.model;

import java.util.List;

/**
 * Represents a parsed request for company LeetCode problems
 */
public class CompanyProblemRequest {

    private List<String> companies;
    private String timeRange;
    private boolean explicitTimeRange;

    public CompanyProblemRequest() {}

    public CompanyProblemRequest(List<String> companies, String timeRange, boolean explicitTimeRange) {
        this.companies = companies;
        this.timeRange = timeRange;
        this.explicitTimeRange = explicitTimeRange;
    }

    public List<String> getCompanies() {
        return companies;
    }

    public void setCompanies(List<String> companies) {
        this.companies = companies;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    public boolean isExplicitTimeRange() {
        return explicitTimeRange;
    }

    public void setExplicitTimeRange(boolean explicitTimeRange) {
        this.explicitTimeRange = explicitTimeRange;
    }

    /**
     * Normalize company name for cache key
     */
    public static String normalizeCompanyName(String company) {
        if (company == null) return null;
        return company.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Generate cache key for DynamoDB
     */
    public static String getCacheKey(String company, String timeRange) {
        String normalizedCompany = normalizeCompanyName(company);
        String normalizedTimeRange = timeRange != null ? timeRange : "last30days";
        return normalizedCompany + "_" + normalizedTimeRange;
    }
}
