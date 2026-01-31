package com.pyrem.leetcodebot.discord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pyrem.leetcodebot.model.LeetCodeProblem;
import com.pyrem.leetcodebot.model.ProblemDifficulty;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Discord embed responses for LeetCode problems.
 */
@Slf4j
public class DiscordResponseBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Discord embed colors (in decimal)
    private static final int COLOR_LEETCODE_ORANGE = 0xFFA116;
    private static final int COLOR_EASY = 0x00B8A3;    // Green
    private static final int COLOR_MEDIUM = 0xFFC01E;  // Yellow/Orange
    private static final int COLOR_HARD = 0xEF4743;    // Red

    /**
     * Build Discord embeds for a list of LeetCode problems.
     *
     * @param company  The company name
     * @param problems The list of problems
     * @return List of embed JSON objects
     */
    public List<ObjectNode> buildProblemEmbeds(String company, List<LeetCodeProblem> problems) {
        List<ObjectNode> embeds = new ArrayList<>();

        // Header embed
        ObjectNode headerEmbed = objectMapper.createObjectNode();
        headerEmbed.put("title", String.format("%s LeetCode Problems", company));
        headerEmbed.put("description", String.format("Found **%d** problems", problems.size()));
        headerEmbed.put("color", COLOR_LEETCODE_ORANGE);
        embeds.add(headerEmbed);

        // Problem embeds (limit to avoid Discord's 10 embed limit minus header)
        int maxProblems = Math.min(problems.size(), 9);
        for (int i = 0; i < maxProblems; i++) {
            embeds.add(createProblemEmbed(problems.get(i)));
        }

        return embeds;
    }

    /**
     * Create a Discord embed for a single problem.
     */
    private ObjectNode createProblemEmbed(LeetCodeProblem problem) {
        ObjectNode embed = objectMapper.createObjectNode();

        // Title with problem number and name, linked to LeetCode
        String title = String.format("%d. %s", problem.getProblemNumber(), problem.getProblemName());
        embed.put("title", title);
        embed.put("url", problem.getUrl());

        // Color based on difficulty
        int color = getColorForDifficulty(problem.getDifficulty());
        embed.put("color", color);

        // Description with problem details
        StringBuilder description = new StringBuilder();

        // Acceptance rate
        String acceptancePercent = String.format("%.1f%%", problem.getAcceptanceRate() * 100);
        description.append("**Acceptance:** ").append(acceptancePercent).append("\n");

        // Difficulty
        description.append("**Difficulty:** ").append(problem.getDifficulty().getDisplayName()).append("\n");

        // Frequency bar
        String frequencyBar = createFrequencyBar(problem.getFrequency());
        description.append("**Frequency:** ").append(frequencyBar);

        embed.put("description", description.toString());

        return embed;
    }

    /**
     * Get the Discord embed color for a difficulty level.
     */
    private int getColorForDifficulty(ProblemDifficulty difficulty) {
        if (difficulty == null) {
            return COLOR_MEDIUM;
        }
        return switch (difficulty) {
            case EASY -> COLOR_EASY;
            case MEDIUM -> COLOR_MEDIUM;
            case HARD -> COLOR_HARD;
        };
    }

    /**
     * Create a visual frequency bar using Unicode characters.
     */
    private String createFrequencyBar(Double frequency) {
        if (frequency == null) {
            frequency = 0.0;
        }

        // Normalize frequency to 0-10 scale
        int barLength = (int) Math.round(frequency * 10);
        barLength = Math.max(0, Math.min(10, barLength));

        StringBuilder bar = new StringBuilder();

        // Use block characters for visual bar
        for (int i = 0; i < 10; i++) {
            if (i < barLength) {
                bar.append("\u2588"); // Full block
            } else {
                bar.append("\u2591"); // Light shade
            }
        }

        // Add percentage
        bar.append(String.format(" %.0f%%", frequency * 100));

        return bar.toString();
    }
}
