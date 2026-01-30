package com.pyrem.leetcodebot.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyrem.leetcodebot.lambda.model.CompanyProblemRequest;
import com.pyrem.leetcodebot.lambda.model.LeetCodeProblem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemFetcherFunctionTest {

    @Mock
    private DynamoDbClient mockDynamoDb;

    @Mock
    private LambdaClient mockLambdaClient;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger;

    private ProblemFetcherFunction handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new ProblemFetcherFunction(objectMapper, mockDynamoDb, mockLambdaClient);

        when(mockContext.getLogger()).thenReturn(mockLogger);
        doNothing().when(mockLogger).log(anyString());
    }

    @Test
    void testCacheMiss() {
        // Given - empty DynamoDB response (cache miss)
        GetItemResponse emptyResponse = GetItemResponse.builder()
            .build(); // No item

        when(mockDynamoDb.getItem(any(GetItemRequest.class)))
            .thenReturn(emptyResponse);

        // Mock Lambda invocation
        InvokeResponse invokeResponse = InvokeResponse.builder()
            .statusCode(200)
            .build();
        when(mockLambdaClient.invoke(any(InvokeRequest.class)))
            .thenReturn(invokeResponse);

        // Mock DynamoDB put
        PutItemResponse putResponse = PutItemResponse.builder()
            .build();
        when(mockDynamoDb.putItem(any(PutItemRequest.class)))
            .thenReturn(putResponse);

        Map<String, Object> event = new HashMap<>();
        event.put("request", Map.of(
            "companies", List.of("Microsoft"),
            "timeRange", "last30days",
            "explicitTimeRange", false
        ));
        event.put("interaction", Map.of("id", "123"));

        // When
        List<LeetCodeProblem> problems = handler.handleRequest(event, mockContext);

        // Then
        assertThat(problems).isNotEmpty();
        assertThat(problems.size()).isGreaterThan(0);

        verify(mockLogger).log(contains("Cache miss"));
        verify(mockLogger).log(contains("fetching from API"));
        verify(mockDynamoDb).getItem(any(GetItemRequest.class));
        verify(mockDynamoDb).putItem(any(PutItemRequest.class));
        verify(mockLambdaClient).invoke(any(InvokeRequest.class));
    }

    @Test
    void testCacheHit() throws Exception {
        // Given - DynamoDB returns cached data
        List<LeetCodeProblem> cachedProblems = List.of(
            new LeetCodeProblem(1, "Two Sum", 0.566, "Easy", 0.95, "https://leetcode.com/problems/two-sum"),
            new LeetCodeProblem(2, "Add Two Numbers", 0.473, "Medium", 0.94, "https://leetcode.com/problems/add-two-numbers")
        );

        String problemsJson = objectMapper.writeValueAsString(cachedProblems);
        long futureTtl = (System.currentTimeMillis() / 1000) + 86400; // 1 day in future

        Map<String, AttributeValue> cachedItem = new HashMap<>();
        cachedItem.put("cache_key", AttributeValue.builder().s("microsoft_last30days").build());
        cachedItem.put("problems", AttributeValue.builder().s(problemsJson).build());
        cachedItem.put("ttl", AttributeValue.builder().n(String.valueOf(futureTtl)).build());
        cachedItem.put("problem_count", AttributeValue.builder().n("2").build());

        GetItemResponse cacheHitResponse = GetItemResponse.builder()
            .item(cachedItem)
            .build();

        when(mockDynamoDb.getItem(any(GetItemRequest.class)))
            .thenReturn(cacheHitResponse);

        // Mock Lambda invocation
        InvokeResponse invokeResponse = InvokeResponse.builder()
            .statusCode(200)
            .build();
        when(mockLambdaClient.invoke(any(InvokeRequest.class)))
            .thenReturn(invokeResponse);

        Map<String, Object> event = new HashMap<>();
        event.put("request", Map.of(
            "companies", List.of("Microsoft"),
            "timeRange", "last30days",
            "explicitTimeRange", false
        ));
        event.put("interaction", Map.of("id", "123"));

        // When
        List<LeetCodeProblem> problems = handler.handleRequest(event, mockContext);

        // Then
        assertThat(problems).hasSize(2);
        assertThat(problems.get(0).getProblemName()).isEqualTo("Two Sum");
        assertThat(problems.get(1).getProblemName()).isEqualTo("Add Two Numbers");

        verify(mockLogger).log(contains("Cache hit"));
        verify(mockDynamoDb).getItem(any(GetItemRequest.class));
        verify(mockDynamoDb, never()).putItem(any(PutItemRequest.class)); // Should not write
        verify(mockLambdaClient).invoke(any(InvokeRequest.class));
    }

    @Test
    void testCacheExpired() throws Exception {
        // Given - DynamoDB returns expired cached data
        List<LeetCodeProblem> expiredProblems = List.of(
            new LeetCodeProblem(1, "Two Sum", 0.566, "Easy", 0.95, "https://leetcode.com/problems/two-sum")
        );

        String problemsJson = objectMapper.writeValueAsString(expiredProblems);
        long pastTtl = (System.currentTimeMillis() / 1000) - 86400; // 1 day in past

        Map<String, AttributeValue> expiredItem = new HashMap<>();
        expiredItem.put("cache_key", AttributeValue.builder().s("microsoft_last30days").build());
        expiredItem.put("problems", AttributeValue.builder().s(problemsJson).build());
        expiredItem.put("ttl", AttributeValue.builder().n(String.valueOf(pastTtl)).build());

        GetItemResponse expiredResponse = GetItemResponse.builder()
            .item(expiredItem)
            .build();

        when(mockDynamoDb.getItem(any(GetItemRequest.class)))
            .thenReturn(expiredResponse);

        // Mock Lambda invocation
        InvokeResponse invokeResponse = InvokeResponse.builder()
            .statusCode(200)
            .build();
        when(mockLambdaClient.invoke(any(InvokeRequest.class)))
            .thenReturn(invokeResponse);

        // Mock DynamoDB put
        PutItemResponse putResponse = PutItemResponse.builder()
            .build();
        when(mockDynamoDb.putItem(any(PutItemRequest.class)))
            .thenReturn(putResponse);

        Map<String, Object> event = new HashMap<>();
        event.put("request", Map.of(
            "companies", List.of("Microsoft"),
            "timeRange", "last30days",
            "explicitTimeRange", false
        ));
        event.put("interaction", Map.of("id", "123"));

        // When
        List<LeetCodeProblem> problems = handler.handleRequest(event, mockContext);

        // Then
        assertThat(problems).isNotEmpty();

        verify(mockLogger).log(contains("Cache expired"));
        verify(mockDynamoDb).getItem(any(GetItemRequest.class));
        verify(mockDynamoDb).putItem(any(PutItemRequest.class)); // Should refresh cache
    }

    @Test
    void testFetchFromAPI() {
        // When
        List<LeetCodeProblem> problems = handler.fetchFromAPI("Microsoft", "last30days", mockContext);

        // Then
        assertThat(problems).isNotEmpty();
        assertThat(problems).allMatch(p -> p.getProblemNumber() > 0);
        assertThat(problems).allMatch(p -> p.getProblemName() != null);
        assertThat(problems).allMatch(p -> p.getDifficulty() != null);

        verify(mockLogger).log(contains("MOCK: Fetching from LeetCode API"));
        verify(mockLogger).log(contains("Mock API returned"));
    }

    @Test
    void testGenerateCacheKey() {
        // When
        String cacheKey1 = CompanyProblemRequest.getCacheKey("Microsoft", "last30days");
        String cacheKey2 = CompanyProblemRequest.getCacheKey("Microsoft", null);
        String cacheKey3 = CompanyProblemRequest.getCacheKey("Google Inc.", "last3months");

        // Then
        assertThat(cacheKey1).isEqualTo("microsoft_last30days");
        assertThat(cacheKey2).isEqualTo("microsoft_last30days"); // null defaults to last30days
        assertThat(cacheKey3).isEqualTo("googleinc_last3months");
    }
}
