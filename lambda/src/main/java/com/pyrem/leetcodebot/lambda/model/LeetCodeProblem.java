package com.pyrem.leetcodebot.lambda.model;

/**
 * Represents a LeetCode problem
 */
public class LeetCodeProblem {

    private int problemNumber;
    private String problemName;
    private double acceptanceRate;
    private String difficulty;
    private double frequency;
    private String url;

    public LeetCodeProblem() {}

    public LeetCodeProblem(int problemNumber, String problemName, double acceptanceRate,
                           String difficulty, double frequency, String url) {
        this.problemNumber = problemNumber;
        this.problemName = problemName;
        this.acceptanceRate = acceptanceRate;
        this.difficulty = difficulty;
        this.frequency = frequency;
        this.url = url;
    }

    // Getters and setters
    public int getProblemNumber() { return problemNumber; }
    public void setProblemNumber(int problemNumber) { this.problemNumber = problemNumber; }

    public String getProblemName() { return problemName; }
    public void setProblemName(String problemName) { this.problemName = problemName; }

    public double getAcceptanceRate() { return acceptanceRate; }
    public void setAcceptanceRate(double acceptanceRate) { this.acceptanceRate = acceptanceRate; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public double getFrequency() { return frequency; }
    public void setFrequency(double frequency) { this.frequency = frequency; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    /**
     * Generate LeetCode URL from problem name if not provided
     */
    public String getOrGenerateUrl() {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        if (problemName != null) {
            String slug = problemName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
            return "https://leetcode.com/problems/" + slug + "/";
        }
        return null;
    }
}
