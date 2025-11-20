package com.pyrem.leetcodebot.service;

import com.pyrem.leetcodebot.model.LeetCodeProblem;
import com.pyrem.leetcodebot.model.ProblemDifficulty;
import com.pyrem.leetcodebot.model.TimeRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mock LeetCode client for testing purposes
 * TODO: Replace with actual LeetCode API integration
 */
@Component
@Slf4j
public class MockLeetCodeClient {

    private final Random random = new Random();

    /**
     * Mock method to fetch problems from LeetCode API
     * Returns mock data for testing
     */
    public List<LeetCodeProblem> fetchProblems(String company, TimeRange timeRange) {
        log.info("MOCK: Fetching problems for company: {}, timeRange: {}", company, timeRange);

        // Simulate different problem counts based on time range
        int problemCount = switch (timeRange) {
            case LAST_30_DAYS -> random.nextInt(20) + 35; // 35-55 problems
            case LAST_3_MONTHS -> random.nextInt(30) + 50; // 50-80 problems
            case LAST_6_MONTHS -> random.nextInt(40) + 70; // 70-110 problems
            case MORE_THAN_6_MONTHS -> random.nextInt(50) + 100; // 100-150 problems
            case ALL -> random.nextInt(100) + 150; // 150-250 problems
        };

        List<LeetCodeProblem> problems = new ArrayList<>();

        // Generate mock problems based on real LeetCode problems
        String[][] mockProblems = {
            {"1", "Two Sum", "Easy"},
            {"2", "Add Two Numbers", "Medium"},
            {"3", "Longest Substring Without Repeating Characters", "Medium"},
            {"7", "Reverse Integer", "Medium"},
            {"9", "Palindrome Number", "Easy"},
            {"13", "Roman to Integer", "Easy"},
            {"14", "Longest Common Prefix", "Easy"},
            {"20", "Valid Parentheses", "Easy"},
            {"21", "Merge Two Sorted Lists", "Easy"},
            {"53", "Maximum Subarray", "Medium"},
            {"121", "Best Time to Buy and Sell Stock", "Easy"},
            {"125", "Valid Palindrome", "Easy"},
            {"206", "Reverse Linked List", "Easy"},
            {"217", "Contains Duplicate", "Easy"},
            {"226", "Invert Binary Tree", "Easy"},
            {"242", "Valid Anagram", "Easy"},
            {"283", "Move Zeroes", "Easy"},
            {"344", "Reverse String", "Easy"},
            {"387", "First Unique Character in a String", "Easy"},
            {"394", "Decode String", "Medium"},
            {"4", "Median of Two Sorted Arrays", "Hard"},
            {"15", "3Sum", "Medium"},
            {"17", "Letter Combinations of a Phone Number", "Medium"},
            {"19", "Remove Nth Node From End of List", "Medium"},
            {"22", "Generate Parentheses", "Medium"},
            {"33", "Search in Rotated Sorted Array", "Medium"},
            {"39", "Combination Sum", "Medium"},
            {"46", "Permutations", "Medium"},
            {"48", "Rotate Image", "Medium"},
            {"49", "Group Anagrams", "Medium"},
            {"56", "Merge Intervals", "Medium"},
            {"75", "Sort Colors", "Medium"},
            {"78", "Subsets", "Medium"},
            {"79", "Word Search", "Medium"},
            {"253", "Meeting Rooms II", "Medium"},
            {"2235", "Add Two Integers", "Easy"}
        };

        // Limit to available mock problems or requested count
        int actualCount = Math.min(problemCount, mockProblems.length);

        for (int i = 0; i < actualCount; i++) {
            String[] mockProblem = mockProblems[i % mockProblems.length];

            // Generate realistic metrics
            double acceptanceRate = 0.3 + (random.nextDouble() * 0.6); // 30% - 90%
            double frequency = 1.0 - (i * 0.01); // Decreasing frequency

            problems.add(LeetCodeProblem.builder()
                .problemNumber(Integer.parseInt(mockProblem[0]))
                .problemName(mockProblem[1])
                .acceptanceRate(acceptanceRate)
                .difficulty(ProblemDifficulty.fromString(mockProblem[2]))
                .frequency(frequency)
                .url("https://leetcode.com/problems/" + mockProblem[1].toLowerCase().replace(" ", "-") + "/")
                .build());
        }

        log.info("MOCK: Returning {} problems for {}", problems.size(), company);
        return problems;
    }
}
