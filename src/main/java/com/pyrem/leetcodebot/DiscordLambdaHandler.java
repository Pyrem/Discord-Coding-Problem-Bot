package com.pyrem.leetcodebot;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pyrem.leetcodebot.discord.DiscordSignatureVerifier;
import com.pyrem.leetcodebot.discord.DiscordResponseBuilder;
import com.pyrem.leetcodebot.model.CompanyProblemRequest;
import com.pyrem.leetcodebot.model.LeetCodeProblem;
import com.pyrem.leetcodebot.nlp.BedrockRequestParser;
import com.pyrem.leetcodebot.service.LeetCodeService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AWS Lambda handler for Discord slash command interactions.
 * Handles Discord webhook requests for LeetCode problem queries.
 */
@Slf4j
public class DiscordLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DiscordSignatureVerifier signatureVerifier = new DiscordSignatureVerifier();
    private static final BedrockRequestParser requestParser = new BedrockRequestParser();
    private static final LeetCodeService leetCodeService = new LeetCodeService();
    private static final DiscordResponseBuilder responseBuilder = new DiscordResponseBuilder();

    // Discord interaction types
    private static final int PING = 1;
    private static final int APPLICATION_COMMAND = 2;

    // Discord response types
    private static final int PONG = 1;
    private static final int CHANNEL_MESSAGE_WITH_SOURCE = 4;
    private static final int DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE = 5;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        log.info("Received Discord interaction request");

        try {
            // Verify Discord signature
            if (!verifySignature(request)) {
                log.warn("Invalid Discord signature");
                return createResponse(401, "{\"error\": \"Invalid request signature\"}");
            }

            // Parse request body
            String body = request.getBody();
            JsonNode interaction = objectMapper.readTree(body);

            int type = interaction.get("type").asInt();

            // Handle PING (verification request from Discord)
            if (type == PING) {
                log.info("Responding to Discord PING verification");
                return createResponse(200, "{\"type\": " + PONG + "}");
            }

            // Handle APPLICATION_COMMAND (slash command)
            if (type == APPLICATION_COMMAND) {
                return handleSlashCommand(interaction);
            }

            log.warn("Unknown interaction type: {}", type);
            return createResponse(400, "{\"error\": \"Unknown interaction type\"}");

        } catch (Exception e) {
            log.error("Error processing Discord interaction: {}", e.getMessage(), e);
            return createErrorResponse("An error occurred processing your request.");
        }
    }

    private boolean verifySignature(APIGatewayProxyRequestEvent request) {
        Map<String, String> headers = request.getHeaders();
        if (headers == null) {
            return false;
        }

        // Discord sends headers in lowercase
        String signature = headers.getOrDefault("x-signature-ed25519",
                          headers.get("X-Signature-Ed25519"));
        String timestamp = headers.getOrDefault("x-signature-timestamp",
                          headers.get("X-Signature-Timestamp"));
        String body = request.getBody();

        if (signature == null || timestamp == null || body == null) {
            return false;
        }

        return signatureVerifier.verify(signature, timestamp, body);
    }

    private APIGatewayProxyResponseEvent handleSlashCommand(JsonNode interaction) {
        try {
            JsonNode data = interaction.get("data");
            String commandName = data.get("name").asText();

            log.info("Processing slash command: {}", commandName);

            if ("leetcode".equals(commandName) || "ask".equals(commandName)) {
                return handleLeetCodeCommand(data);
            }

            return createErrorResponse("Unknown command: " + commandName);

        } catch (Exception e) {
            log.error("Error handling slash command: {}", e.getMessage(), e);
            return createErrorResponse("Failed to process command.");
        }
    }

    private APIGatewayProxyResponseEvent handleLeetCodeCommand(JsonNode data) {
        try {
            // Extract the user's natural language query from command options
            String query = extractQueryFromOptions(data);

            if (query == null || query.isBlank()) {
                return createTextResponse("Please provide a query. Example: `/leetcode Google problems from last 30 days`");
            }

            log.info("Processing LeetCode query: {}", query);

            // Parse the natural language request using Bedrock
            CompanyProblemRequest request = requestParser.parseRequest(query);

            if (request.getCompanies() == null || request.getCompanies().isEmpty()) {
                return createTextResponse("I couldn't identify any company names in your request. " +
                    "Try something like: `Microsoft problems` or `Google 30 days`");
            }

            // Build response with problems for each company
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", CHANNEL_MESSAGE_WITH_SOURCE);

            ObjectNode responseData = objectMapper.createObjectNode();
            ArrayNode embeds = objectMapper.createArrayNode();

            for (String company : request.getCompanies()) {
                List<LeetCodeProblem> problems = leetCodeService.getProblems(
                    company,
                    request.getTimeRange(),
                    request.isExplicitTimeRange()
                );

                // Add embeds for this company's problems
                List<ObjectNode> companyEmbeds = responseBuilder.buildProblemEmbeds(company, problems);
                companyEmbeds.forEach(embeds::add);
            }

            // Discord allows max 10 embeds per message
            if (embeds.size() > 10) {
                ArrayNode limitedEmbeds = objectMapper.createArrayNode();
                for (int i = 0; i < 10; i++) {
                    limitedEmbeds.add(embeds.get(i));
                }
                responseData.set("embeds", limitedEmbeds);
                responseData.put("content", String.format("Showing first 10 of %d problems. Use a more specific time range to see fewer results.", embeds.size()));
            } else {
                responseData.set("embeds", embeds);
            }

            response.set("data", responseData);

            return createResponse(200, objectMapper.writeValueAsString(response));

        } catch (Exception e) {
            log.error("Error handling LeetCode command: {}", e.getMessage(), e);
            return createErrorResponse("Failed to fetch LeetCode problems. Please try again.");
        }
    }

    private String extractQueryFromOptions(JsonNode data) {
        JsonNode options = data.get("options");
        if (options != null && options.isArray()) {
            for (JsonNode option : options) {
                String name = option.get("name").asText();
                if ("query".equals(name) || "message".equals(name) || "company".equals(name)) {
                    return option.get("value").asText();
                }
            }
        }
        return null;
    }

    private APIGatewayProxyResponseEvent createTextResponse(String message) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", CHANNEL_MESSAGE_WITH_SOURCE);

            ObjectNode data = objectMapper.createObjectNode();
            data.put("content", message);
            response.set("data", data);

            return createResponse(200, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            return createResponse(500, "{\"error\": \"Internal error\"}");
        }
    }

    private APIGatewayProxyResponseEvent createErrorResponse(String message) {
        return createTextResponse(message);
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setHeaders(headers);
        response.setBody(body);

        return response;
    }
}
