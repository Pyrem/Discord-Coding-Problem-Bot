package com.pyrem.leetcodebot.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyrem.leetcodebot.lambda.model.CompanyProblemRequest;
import com.pyrem.leetcodebot.lambda.model.LeetCodeProblem;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;

import java.util.*;

/**
 * Lambda handler for fetching LeetCode problems
 * Checks DynamoDB cache first, then fetches from API if needed
 */
public class ProblemFetcherFunction implements RequestHandler<Map<String, Object>, List<LeetCodeProblem>> {

    private final ObjectMapper objectMapper;
    private final DynamoDbClient dynamoDb;
    private final LambdaClient lambdaClient;

    public ProblemFetcherFunction() {
        this.objectMapper = new ObjectMapper();
        this.dynamoDb = DynamoDbClient.create();
        this.lambdaClient = LambdaClient.create();
    }

    // For testing
    public ProblemFetcherFunction(ObjectMapper objectMapper, DynamoDbClient dynamoDb,
                                   LambdaClient lambdaClient) {
        this.objectMapper = objectMapper;
        this.dynamoDb = dynamoDb;
        this.lambdaClient = lambdaClient;
    }

    @Override
    public List<LeetCodeProblem> handleRequest(Map<String, Object> event, Context context) {
        try {
            // Extract request
            Map<String, Object> requestMap = (Map<String, Object>) event.get("request");
            CompanyProblemRequest request = objectMapper.convertValue(requestMap, CompanyProblemRequest.class);

            String company = request.getCompanies().get(0);
            String timeRange = request.getTimeRange() != null ? request.getTimeRange() : "last30days";

            context.getLogger().log("Fetching problems for: " + company + " (" + timeRange + ")");

            // Generate cache key
            String cacheKey = CompanyProblemRequest.getCacheKey(company, timeRange);

            // Check cache
            List<LeetCodeProblem> problems = checkCache(cacheKey, context);

            if (problems == null) {
                context.getLogger().log("Cache miss, fetching from API");
                problems = fetchFromAPI(company, timeRange, context);
                storeInCache(cacheKey, company, timeRange, problems, context);
            }

            // Invoke response formatter
            invokeResponseFormatter(event.get("interaction"), problems, context);

            return problems;

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    List<LeetCodeProblem> checkCache(String cacheKey, Context context) {
        try {
            String tableName = System.getenv("DYNAMODB_TABLE");
            if (tableName == null) tableName = "leetcode_problem_sets";

            GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("cache_key", AttributeValue.builder().s(cacheKey).build()))
                .build();

            GetItemResponse response = dynamoDb.getItem(request);

            if (!response.hasItem()) {
                context.getLogger().log("Cache miss for: " + cacheKey);
                return null;
            }

            Map<String, AttributeValue> item = response.item();

            // Check TTL
            if (item.containsKey("ttl")) {
                long ttl = Long.parseLong(item.get("ttl").n());
                long now = System.currentTimeMillis() / 1000;
                if (now > ttl) {
                    context.getLogger().log("Cache expired for: " + cacheKey);
                    return null;
                }
            }

            // Deserialize problems
            String problemsJson = item.get("problems").s();
            List<LeetCodeProblem> problems = objectMapper.readValue(
                problemsJson,
                new TypeReference<List<LeetCodeProblem>>() {}
            );

            context.getLogger().log("Cache hit! Found " + problems.size() + " problems");
            return problems;

        } catch (Exception e) {
            context.getLogger().log("Cache check error: " + e.getMessage());
            return null;
        }
    }

    List<LeetCodeProblem> fetchFromAPI(String company, String timeRange, Context context) {
        context.getLogger().log("MOCK: Fetching from LeetCode API");

        // Mock implementation - in production, call actual LeetCode API
        List<LeetCodeProblem> problems = new ArrayList<>();

        String[][] mockData = {
            {"1", "Two Sum", "0.566", "Easy", "0.95"},
            {"2", "Add Two Numbers", "0.473", "Medium", "0.94"},
            {"3", "Longest Substring Without Repeating Characters", "0.379", "Medium", "0.93"},
            {"7", "Reverse Integer", "0.311", "Medium", "0.92"},
            {"9", "Palindrome Number", "0.599", "Easy", "0.91"},
            {"14", "Longest Common Prefix", "0.465", "Easy", "0.90"},
            {"121", "Best Time to Buy and Sell Stock", "0.560", "Easy", "0.89"},
            {"253", "Meeting Rooms II", "0.524", "Medium", "0.88"},
            {"394", "Decode String", "0.619", "Medium", "0.87"},
            {"4", "Median of Two Sorted Arrays", "0.452", "Hard", "0.86"}
        };

        for (String[] data : mockData) {
            problems.add(new LeetCodeProblem(
                Integer.parseInt(data[0]),
                data[1],
                Double.parseDouble(data[2]),
                data[3],
                Double.parseDouble(data[4]),
                "https://leetcode.com/problems/" + data[1].toLowerCase().replace(" ", "-")
            ));
        }

        context.getLogger().log("Mock API returned " + problems.size() + " problems");
        return problems;
    }

    void storeInCache(String cacheKey, String company, String timeRange,
                      List<LeetCodeProblem> problems, Context context) {
        try {
            String tableName = System.getenv("DYNAMODB_TABLE");
            if (tableName == null) tableName = "leetcode_problem_sets";

            long ttl = System.currentTimeMillis() / 1000 + (30 * 86400); // 30 days
            String problemsJson = objectMapper.writeValueAsString(problems);

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("cache_key", AttributeValue.builder().s(cacheKey).build());
            item.put("company", AttributeValue.builder().s(company).build());
            item.put("time_range", AttributeValue.builder().s(timeRange).build());
            item.put("problem_count", AttributeValue.builder().n(String.valueOf(problems.size())).build());
            item.put("problems", AttributeValue.builder().s(problemsJson).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
            item.put("last_updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());

            PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

            dynamoDb.putItem(request);

            context.getLogger().log("Stored " + problems.size() + " problems in cache");

        } catch (Exception e) {
            context.getLogger().log("Cache store error: " + e.getMessage());
        }
    }

    private void invokeResponseFormatter(Object interaction, List<LeetCodeProblem> problems, Context context) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("interaction", interaction);
        payload.put("problems", problems);

        String json = objectMapper.writeValueAsString(payload);

        software.amazon.awssdk.services.lambda.model.InvokeRequest request =
            software.amazon.awssdk.services.lambda.model.InvokeRequest.builder()
                .functionName(System.getenv("RESPONSE_FORMATTER_FUNCTION"))
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(json))
                .build();

        lambdaClient.invoke(request);
        context.getLogger().log("Invoked ResponseFormatterFunction");
    }
}
