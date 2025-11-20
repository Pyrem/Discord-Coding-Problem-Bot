package com.pyrem.leetcodebot.service;

import com.pyrem.leetcodebot.model.*;
import com.pyrem.leetcodebot.repository.CachedProblemSetRepository;
import com.pyrem.leetcodebot.repository.DynamicProblemSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing LeetCode problem sets with caching and automatic time range selection
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeetCodeService {

    private final CachedProblemSetRepository cachedProblemSetRepository;
    private final DynamicProblemSetRepository dynamicProblemSetRepository;
    private final MockLeetCodeClient mockLeetCodeClient;

    @Value("${leetcode.cache.expiry.days:30}")
    private int cacheExpiryDays;

    @Value("${leetcode.problemset.min.size:30}")
    private int minProblemSetSize;

    @Value("${leetcode.problemset.max.size:50}")
    private int maxProblemSetSize;

    /**
     * Get problems for a company, using cache if available and not expired
     * Implements automatic time range selection if not explicitly specified
     */
    @Transactional
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
        String tableName = CompanyProblemRequest.getTableName(normalizedCompany, timeRange);

        // Check if cached and not expired
        CachedProblemSet cached = cachedProblemSetRepository.findByTableName(tableName).orElse(null);

        if (cached != null && !cached.isExpired(cacheExpiryDays)) {
            log.info("Using cached problem set from table: {}", tableName);
            return dynamicProblemSetRepository.findAllProblems(tableName);
        }

        // Cache miss or expired, fetch fresh data
        log.info("Cache miss or expired for {}, fetching from API", tableName);
        return fetchAndCacheProblems(normalizedCompany, displayCompany, timeRange, tableName);
    }

    /**
     * Automatically select the best time range (most recent with at least minProblemSetSize problems)
     */
    private List<LeetCodeProblem> getProblemsWithAutoTimeRange(String normalizedCompany, String displayCompany) {
        log.info("Auto-selecting time range for company: {}", normalizedCompany);

        // Try each time range from most recent to oldest
        for (TimeRange timeRange : TimeRange.values()) {
            String tableName = CompanyProblemRequest.getTableName(normalizedCompany, timeRange);

            // Check cache first
            CachedProblemSet cached = cachedProblemSetRepository.findByTableName(tableName).orElse(null);

            if (cached != null && !cached.isExpired(cacheExpiryDays) && cached.getProblemCount() >= minProblemSetSize) {
                log.info("Found cached problem set with {} problems in range: {}", cached.getProblemCount(), timeRange);
                return dynamicProblemSetRepository.findAllProblems(tableName);
            }

            // Try fetching fresh data
            List<LeetCodeProblem> problems = mockLeetCodeClient.fetchProblems(displayCompany, timeRange);

            if (problems.size() >= minProblemSetSize) {
                log.info("Found {} problems in range: {}, caching...", problems.size(), timeRange);
                return cacheProblems(normalizedCompany, timeRange, tableName, problems);
            }

            log.info("Only {} problems in range: {}, trying wider range...", problems.size(), timeRange);
        }

        // If we get here, even "ALL" doesn't have enough problems
        // Return whatever we have from "ALL"
        TimeRange allRange = TimeRange.ALL;
        String tableName = CompanyProblemRequest.getTableName(normalizedCompany, allRange);
        List<LeetCodeProblem> allProblems = mockLeetCodeClient.fetchProblems(displayCompany, allRange);

        log.warn("Could not find {} problems for company: {}, returning all {} problems",
            minProblemSetSize, normalizedCompany, allProblems.size());

        return cacheProblems(normalizedCompany, allRange, tableName, allProblems);
    }

    /**
     * Fetch problems from API and cache them
     */
    private List<LeetCodeProblem> fetchAndCacheProblems(String normalizedCompany, String displayCompany,
                                                         TimeRange timeRange, String tableName) {
        // Fetch from API
        List<LeetCodeProblem> problems = mockLeetCodeClient.fetchProblems(displayCompany, timeRange);

        return cacheProblems(normalizedCompany, timeRange, tableName, problems);
    }

    /**
     * Cache problems in the database
     */
    private List<LeetCodeProblem> cacheProblems(String normalizedCompany, TimeRange timeRange,
                                                 String tableName, List<LeetCodeProblem> problems) {
        // Limit to max size
        List<LeetCodeProblem> limitedProblems = problems.size() > maxProblemSetSize
            ? problems.subList(0, maxProblemSetSize)
            : problems;

        // Create table if it doesn't exist
        if (!dynamicProblemSetRepository.tableExists(tableName)) {
            dynamicProblemSetRepository.createProblemSetTable(tableName);
        }

        // Save problems
        dynamicProblemSetRepository.saveProblems(tableName, limitedProblems);

        // Update or create cache metadata
        CachedProblemSet cached = cachedProblemSetRepository.findByTableName(tableName)
            .orElse(CachedProblemSet.builder()
                .companyName(normalizedCompany)
                .timeRange(timeRange.getKey())
                .tableName(tableName)
                .build());

        cached.setProblemCount(limitedProblems.size());
        cached.setLastUpdated(LocalDateTime.now());

        cachedProblemSetRepository.save(cached);

        log.info("Cached {} problems for {}", limitedProblems.size(), tableName);

        return limitedProblems;
    }

    /**
     * Invalidate cache for a specific company and time range
     */
    public void invalidateCache(String company, TimeRange timeRange) {
        String normalizedCompany = CompanyProblemRequest.normalizeCompanyName(company);
        String tableName = CompanyProblemRequest.getTableName(normalizedCompany, timeRange);

        cachedProblemSetRepository.findByTableName(tableName).ifPresent(cached -> {
            log.info("Invalidating cache for: {}", tableName);
            cachedProblemSetRepository.delete(cached);
            dynamicProblemSetRepository.dropTable(tableName);
        });
    }
}
