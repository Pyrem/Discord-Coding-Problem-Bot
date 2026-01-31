package com.pyrem.leetcodebot.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyrem.leetcodebot.lambda.model.DiscordInteraction;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for Discord webhook events
 * Entry point for all Discord interactions
 */
public class DiscordEventHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper;
    private final LambdaClient lambdaClient;

    // For production use
    public DiscordEventHandler() {
        this.objectMapper = new ObjectMapper();
        this.lambdaClient = LambdaClient.create();
    }

    // For testing - allows dependency injection
    public DiscordEventHandler(ObjectMapper objectMapper, LambdaClient lambdaClient) {
        this.objectMapper = objectMapper;
        this.lambdaClient = lambdaClient;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            context.getLogger().log("Received Discord event");

            // Parse interaction
            DiscordInteraction interaction = objectMapper.readValue(
                event.getBody(),
                DiscordInteraction.class
            );

            // Handle PING (Discord verification)
            if (interaction.getType() == 1) {
                context.getLogger().log("Responding to PING");
                return createResponse(200, "{\"type\":1}");
            }

            // Handle APPLICATION_COMMAND
            if (interaction.getType() == 2) {
                context.getLogger().log("Processing APPLICATION_COMMAND");

                // Extract message
                String message = extractMessage(interaction);
                context.getLogger().log("Message: " + message);

                // Invoke async processing
                invokeRequestParser(interaction, message);

                // Return deferred response
                return createDeferredResponse();
            }

            return createResponse(400, "{\"error\":\"Unknown interaction type\"}");

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, "{\"error\":\"Internal server error\"}");
        }
    }

    private String extractMessage(DiscordInteraction interaction) {
        if (interaction.getData() != null &&
            interaction.getData().getOptions() != null &&
            interaction.getData().getOptions().length > 0) {
            return interaction.getData().getOptions()[0].getValue();
        }
        return "";
    }

    private void invokeRequestParser(DiscordInteraction interaction, String message) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("interaction", interaction);
        payload.put("message", message);

        String json = objectMapper.writeValueAsString(payload);

        InvokeRequest request = InvokeRequest.builder()
            .functionName(System.getenv("REQUEST_PARSER_FUNCTION"))
            .invocationType(InvocationType.EVENT) // Async
            .payload(SdkBytes.fromUtf8String(json))
            .build();

        lambdaClient.invoke(request);
    }

    private APIGatewayProxyResponseEvent createDeferredResponse() {
        return createResponse(200, "{\"type\":5}");
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setBody(body);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        response.setHeaders(headers);

        return response;
    }
}
