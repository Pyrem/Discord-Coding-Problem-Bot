package com.pyrem.leetcodebot.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyrem.leetcodebot.lambda.model.CompanyProblemRequest;
import com.pyrem.leetcodebot.lambda.model.DiscordInteraction;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for parsing natural language requests using AWS Bedrock
 */
public class RequestParserFunction implements RequestHandler<Map<String, Object>, CompanyProblemRequest> {

    private final ObjectMapper objectMapper;
    private final BedrockRuntimeClient bedrockClient;
    private final LambdaClient lambdaClient;

    public RequestParserFunction() {
        this.objectMapper = new ObjectMapper();
        this.bedrockClient = BedrockRuntimeClient.create();
        this.lambdaClient = LambdaClient.create();
    }

    // For testing
    public RequestParserFunction(ObjectMapper objectMapper, BedrockRuntimeClient bedrockClient,
                                  LambdaClient lambdaClient) {
        this.objectMapper = objectMapper;
        this.bedrockClient = bedrockClient;
        this.lambdaClient = lambdaClient;
    }

    @Override
    public CompanyProblemRequest handleRequest(Map<String, Object> event, Context context) {
        try {
            String message = (String) event.get("message");
            context.getLogger().log("Parsing message: " + message);

            // Call Bedrock for NLP
            CompanyProblemRequest request = parseWithBedrock(message, context);

            context.getLogger().log("Parsed companies: " + request.getCompanies());
            context.getLogger().log("Time range: " + request.getTimeRange());

            // Invoke problem fetcher
            invokeProblemFetcher(event.get("interaction"), request, context);

            return request;

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    CompanyProblemRequest parseWithBedrock(String message, Context context) throws Exception {
        String prompt = createPrompt(message);

        // Create Bedrock request
        String requestBody = String.format("""
            {
              "anthropic_version": "bedrock-2023-05-31",
              "max_tokens": 500,
              "temperature": 0.3,
              "messages": [
                {
                  "role": "user",
                  "content": "%s"
                }
              ]
            }
            """, prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        InvokeModelRequest request = InvokeModelRequest.builder()
            .modelId("anthropic.claude-3-haiku-20240307-v1:0")
            .body(SdkBytes.fromUtf8String(requestBody))
            .build();

        context.getLogger().log("Calling Bedrock...");
        InvokeModelResponse response = bedrockClient.invokeModel(request);

        String responseBody = response.body().asUtf8String();
        context.getLogger().log("Bedrock response: " + responseBody);

        return parseBedrockResponse(responseBody);
    }

    private String createPrompt(String message) {
        return String.format("""
            Extract company names and time range from this message: "%s"

            Respond ONLY with JSON in this format:
            {"companies": ["Company1"], "timeRange": "last30days", "explicitTimeRange": false}

            Rules:
            - Normalize company names (e.g., "microsoft" -> "Microsoft")
            - If no time specified: timeRange=null, explicitTimeRange=false
            - Valid time ranges: last30days, last3months, last6months, all

            Examples:
            "Microsoft?" -> {"companies":["Microsoft"],"timeRange":null,"explicitTimeRange":false}
            "Google last 30 days" -> {"companies":["Google"],"timeRange":"last30days","explicitTimeRange":true}
            """, message);
    }

    CompanyProblemRequest parseBedrockResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // Extract JSON from Bedrock response format
        String jsonText = root.get("content").get(0).get("text").asText();

        // Parse the extracted JSON
        JsonNode parsed = objectMapper.readTree(jsonText);

        List<String> companies = new ArrayList<>();
        if (parsed.has("companies") && parsed.get("companies").isArray()) {
            parsed.get("companies").forEach(c -> companies.add(c.asText()));
        }

        String timeRange = null;
        boolean explicitTimeRange = false;

        if (parsed.has("timeRange") && !parsed.get("timeRange").isNull()) {
            timeRange = parsed.get("timeRange").asText();
            if (!"null".equals(timeRange)) {
                explicitTimeRange = parsed.has("explicitTimeRange") &&
                                   parsed.get("explicitTimeRange").asBoolean();
            } else {
                timeRange = null;
            }
        }

        return new CompanyProblemRequest(companies, timeRange, explicitTimeRange);
    }

    private void invokeProblemFetcher(Object interaction, CompanyProblemRequest request, Context context) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("interaction", interaction);
        payload.put("request", request);

        String json = objectMapper.writeValueAsString(payload);

        software.amazon.awssdk.services.lambda.model.InvokeRequest invokeRequest =
            software.amazon.awssdk.services.lambda.model.InvokeRequest.builder()
                .functionName(System.getenv("PROBLEM_FETCHER_FUNCTION"))
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(json))
                .build();

        lambdaClient.invoke(invokeRequest);
        context.getLogger().log("Invoked ProblemFetcherFunction");
    }
}
