package com.pyrem.leetcodebot.service;

import com.pyrem.leetcodebot.model.*;
import com.pyrem.leetcodebot.repository.DynamoDbRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing LeetCode problem sets with caching and automatic time range selection.
 * Uses DynamoDB for persistence.
 */
@Slf4j
public class LeetCodeService {

    private final DynamoDbRepository repository;
    private final MockLeetCodeClient mockLeetCodeClient;

    private final int cacheExpiryDays;
    private final int minProblemSetSize;
    private final int maxProblemSetSize;

    public LeetCodeService() {
        this.repository = new DynamoDbRepository();
        this.mockLeetCodeClient = new MockLeetCodeClient();

        // Read configuration from environment variables
        this.cacheExpiryDays = Integer.parseInt(
            System.getenv().getOrDefault("CACHE_EXPIRY_DAYS", "30"));
        this.minProblemSetSize = Integer.parseInt(
            System.getenv().getOrDefault("MIN_PROBLEM_SET_SIZE", "30"));
        this.maxProblemSetSize = Integer.parseInt(
            System.getenv().getOrDefault("MAX_PROBLEM_SET_SIZE", "50"));
    }

    /**
     * Get problems for a company, using cache if available and not expired.
     * Implements automatic time range selection if not explicitly specified.
     */
    public List<LeetCodeProblem> getProblems(String company, TimeRange requestedTimeRange, boolean explicitTimeRange) {
        log.info("Getting problems for company: {}, timeRange: {}, explicit: {}",
            company, requestedTimeRange, explicitTimeRange);

        String normalizedCompany = CompanyProblemRequest.normalizeCompanyName(company);

        if (explicitTimeRange) {
            // User explicitly requested a time range, use it directly
            return getProblemsForTimeRange(normalizedCompany, company, requestedTimeRange);
        } else {
            // Automatic time range selection: find the most recent range with at least minProblemSetSize problems
            return getProblemsWithAutoTimeRange(normalizedCompany, company);
        }
    }

    /**
     * Get problems for a specific time range
     */
    private List<LeetCodeProblem> getProblemsForTimeRange(String normalizedCompany, String displayCompany, TimeRange timeRange) {
        String timeRangeKey = timeRange.getKey();

        // Check if cached and not expired
        var cached = repository.getCacheMetadata(normalizedCompany, timeRangeKey);

        if (cached.isPresent() && !cached.get().isExpired(cacheExpiryDays)) {
            log.info("Using cached problem set for: {}/{}", normalizedCompany, timeRangeKey);
            return repository.getProblems(normalizedCompany, timeRangeKey);
        }

        // Cache miss or expired, fetch fresh data
        log.info("Cache miss or expired for {}/{}, fetching from API", normalizedCompany, timeRangeKey);
        return fetchAndCacheProblems(normalizedCompany, displayCompany, timeRange);
    }

    /**
     * Automatically select the best time range (most recent with at least minProblemSetSize problems)
     */
    private List<LeetCodeProblem> getProblemsWithAutoTimeRange(String normalizedCompany, String displayCompany) {
        log.info("Auto-selecting time range for company: {}", normalizedCompany);

        // Try each time range from most recent to oldest
        for (TimeRange timeRange : TimeRange.values()) {
            String timeRangeKey = timeRange.getKey();

            // Check cache first
            var cached = repository.getCacheMetadata(normalizedCompany, timeRangeKey);

            if (cached.isPresent() && !cached.get().isExpired(cacheExpiryDays)
                    && cached.get().getProblemCount() >= minProblemSetSize) {
                log.info("Found cached problem set with {} problems in range: {}",
                    cached.get().getProblemCount(), timeRange);
                return repository.getProblems(normalizedCompany, timeRangeKey);
            }

            // Try fetching fresh data
            List<LeetCodeProblem> problems = mockLeetCodeClient.fetchProblems(displayCompany, timeRange);

            if (problems.size() >= minProblemSetSize) {
                log.info("Found {} problems in range: {}, caching...", problems.size(), timeRange);
                return cacheProblems(normalizedCompany, timeRange, problems);
            }

            log.info("Only {} problems in range: {}, trying wider range...", problems.size(), timeRange);
        }

        // If we get here, even "ALL" doesn't have enough problems
        // Return whatever we have from "ALL"
        TimeRange allRange = TimeRange.ALL;
        List<LeetCodeProblem> allProblems = mockLeetCodeClient.fetchProblems(displayCompany, allRange);

        log.warn("Could not find {} problems for company: {}, returning all {} problems",
            minProblemSetSize, normalizedCompany, allProblems.size());

        return cacheProblems(normalizedCompany, allRange, allProblems);
    }

    /**
     * Fetch problems from API and cache them
     */
    private List<LeetCodeProblem> fetchAndCacheProblems(String normalizedCompany, String displayCompany,
                                                         TimeRange timeRange) {
        // Fetch from API
        List<LeetCodeProblem> problems = mockLeetCodeClient.fetchProblems(displayCompany, timeRange);
        return cacheProblems(normalizedCompany, timeRange, problems);
    }

    /**
     * Cache problems in DynamoDB
     */
    private List<LeetCodeProblem> cacheProblems(String normalizedCompany, TimeRange timeRange,
                                                 List<LeetCodeProblem> problems) {
        // Limit to max size
        List<LeetCodeProblem> limitedProblems = problems.size() > maxProblemSetSize
            ? problems.subList(0, maxProblemSetSize)
            : problems;

        String timeRangeKey = timeRange.getKey();

        // Save problems to DynamoDB
        repository.saveProblems(normalizedCompany, timeRangeKey, limitedProblems);

        // Save cache metadata
        CachedProblemSet cacheMetadata = CachedProblemSet.builder()
            .companyName(normalizedCompany)
            .timeRange(timeRangeKey)
            .problemCount(limitedProblems.size())
            .lastUpdated(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .build();

        repository.saveCacheMetadata(cacheMetadata);

        log.info("Cached {} problems for {}/{}", limitedProblems.size(), normalizedCompany, timeRangeKey);

        return limitedProblems;
    }

    /**
     * Invalidate cache for a specific company and time range
     */
    public void invalidateCache(String company, TimeRange timeRange) {
        String normalizedCompany = CompanyProblemRequest.normalizeCompanyName(company);
        String timeRangeKey = timeRange.getKey();

        log.info("Invalidating cache for: {}/{}", normalizedCompany, timeRangeKey);
        repository.deleteProblems(normalizedCompany, timeRangeKey);
    }
}
