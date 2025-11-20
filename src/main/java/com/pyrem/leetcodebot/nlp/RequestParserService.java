package com.pyrem.leetcodebot.nlp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyrem.leetcodebot.model.CompanyProblemRequest;
import com.pyrem.leetcodebot.model.TimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for parsing natural language Discord messages into structured CompanyProblemRequest objects
 * Uses Spring AI with Ollama (llama3.2) for NLP processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestParserService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String PARSING_PROMPT = """
        You are a helpful assistant that extracts structured information from user requests about LeetCode problems.

        Extract the following information from the user's message:
        1. Company names (e.g., Microsoft, Google, Amazon, Meta, Apple)
        2. Time range if specified (e.g., "30 days", "3 months", "6 months", "all time")

        User message: "{message}"

        Respond ONLY with a JSON object in this exact format (no additional text):
        {{
          "companies": ["Company1", "Company2"],
          "timeRange": "last30days|last3months|last6months|morethan6months|all|null",
          "explicitTimeRange": true|false
        }}

        Rules:
        - If no companies are mentioned, return an empty array
        - If no time range is specified, set timeRange to null and explicitTimeRange to false
        - If a time range is specified, set explicitTimeRange to true
        - Normalize company names to proper case (e.g., "microsoft" -> "Microsoft")
        - For time ranges: map "30 days" to "last30days", "3 months" to "last3months", etc.

        Examples:
        - "Microsoft?" -> {{"companies": ["Microsoft"], "timeRange": null, "explicitTimeRange": false}}
        - "Google problems from last 30 days" -> {{"companies": ["Google"], "timeRange": "last30days", "explicitTimeRange": true}}
        - "Amazon and Meta 6 months" -> {{"companies": ["Amazon", "Meta"], "timeRange": "last6months", "explicitTimeRange": true}}
        """;

    /**
     * Parse a natural language message into a structured CompanyProblemRequest
     */
    public CompanyProblemRequest parseRequest(String message) {
        log.info("Parsing request: {}", message);

        try {
            // Create chat client
            ChatClient chatClient = chatClientBuilder.build();

            // Create prompt
            PromptTemplate promptTemplate = new PromptTemplate(PARSING_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of("message", message));

            // Call Ollama via Spring AI
            String response = chatClient.prompt(prompt)
                .call()
                .content();

            log.debug("LLM Response: {}", response);

            // Parse JSON response
            return parseJsonResponse(response);

        } catch (Exception e) {
            log.error("Error parsing request with LLM: {}", e.getMessage(), e);
            // Fallback to simple parsing
            return fallbackParsing(message);
        }
    }

    /**
     * Parse the JSON response from the LLM
     */
    private CompanyProblemRequest parseJsonResponse(String jsonResponse) throws JsonProcessingException {
        // Extract JSON from response (LLM might include extra text)
        String cleanJson = extractJson(jsonResponse);

        JsonNode node = objectMapper.readTree(cleanJson);

        List<String> companies = new ArrayList<>();
        if (node.has("companies") && node.get("companies").isArray()) {
            node.get("companies").forEach(c -> companies.add(c.asText()));
        }

        TimeRange timeRange = null;
        boolean explicitTimeRange = false;

        if (node.has("timeRange") && !node.get("timeRange").isNull()) {
            String timeRangeStr = node.get("timeRange").asText();
            if (!"null".equals(timeRangeStr)) {
                timeRange = TimeRange.fromString(timeRangeStr);
                explicitTimeRange = node.has("explicitTimeRange") && node.get("explicitTimeRange").asBoolean();
            }
        }

        return CompanyProblemRequest.builder()
            .companies(companies)
            .timeRange(timeRange)
            .explicitTimeRange(explicitTimeRange)
            .build();
    }

    /**
     * Extract JSON from LLM response (removes markdown code blocks, etc.)
     */
    private String extractJson(String response) {
        // Remove markdown code blocks
        String cleaned = response.replaceAll("```json\\s*", "")
            .replaceAll("```\\s*", "")
            .trim();

        // Find JSON object boundaries
        int startIdx = cleaned.indexOf('{');
        int endIdx = cleaned.lastIndexOf('}');

        if (startIdx >= 0 && endIdx > startIdx) {
            return cleaned.substring(startIdx, endIdx + 1);
        }

        return cleaned;
    }

    /**
     * Fallback parsing using simple string matching when LLM fails
     */
    private CompanyProblemRequest fallbackParsing(String message) {
        log.info("Using fallback parsing for: {}", message);

        List<String> companies = extractCompaniesSimple(message);
        TimeRange timeRange = extractTimeRangeSimple(message);
        boolean explicitTimeRange = timeRange != null;

        return CompanyProblemRequest.builder()
            .companies(companies)
            .timeRange(timeRange)
            .explicitTimeRange(explicitTimeRange)
            .build();
    }

    /**
     * Simple company extraction using keyword matching
     */
    private List<String> extractCompaniesSimple(String message) {
        List<String> companies = new ArrayList<>();
        String lower = message.toLowerCase();

        // Common company names
        String[] companyNames = {
            "Microsoft", "Google", "Amazon", "Meta", "Facebook",
            "Apple", "Netflix", "Tesla", "Uber", "Lyft",
            "Airbnb", "LinkedIn", "Twitter", "Snapchat", "Adobe",
            "Oracle", "Salesforce", "IBM", "Intel", "Nvidia"
        };

        for (String company : companyNames) {
            if (lower.contains(company.toLowerCase())) {
                companies.add(company);
            }
        }

        return companies;
    }

    /**
     * Simple time range extraction using keyword matching
     */
    private TimeRange extractTimeRangeSimple(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("30") && lower.contains("day")) {
            return TimeRange.LAST_30_DAYS;
        } else if (lower.contains("3") && lower.contains("month")) {
            return TimeRange.LAST_3_MONTHS;
        } else if (lower.contains("6") && lower.contains("month")) {
            return TimeRange.LAST_6_MONTHS;
        } else if (lower.contains("all")) {
            return TimeRange.ALL;
        }

        return null; // No explicit time range
    }
}
