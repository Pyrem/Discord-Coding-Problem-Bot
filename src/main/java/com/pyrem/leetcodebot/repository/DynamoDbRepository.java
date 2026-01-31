package com.pyrem.leetcodebot.repository;

import com.pyrem.leetcodebot.model.CachedProblemSet;
import com.pyrem.leetcodebot.model.LeetCodeProblem;
import com.pyrem.leetcodebot.model.ProblemDifficulty;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * DynamoDB repository for LeetCode problems and cache metadata.
 * Uses single-table design with composite keys.
 *
 * Key Schema:
 * - PK: COMPANY#{normalizedCompany}
 * - SK: RANGE#{timeRangeKey}#PROBLEM#{problemNumber} (for problems)
 *       RANGE#{timeRangeKey}#METADATA (for cache metadata)
 *
 * GSI1 (for querying all problems by company):
 * - GSI1PK: COMPANY#{normalizedCompany}#RANGE#{timeRangeKey}
 * - GSI1SK: PROBLEM#{problemNumber}
 */
@Slf4j
public class DynamoDbRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    // Key prefixes
    private static final String COMPANY_PREFIX = "COMPANY#";
    private static final String RANGE_PREFIX = "RANGE#";
    private static final String PROBLEM_PREFIX = "PROBLEM#";
    private static final String METADATA_SUFFIX = "METADATA";

    public DynamoDbRepository() {
        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }

        this.dynamoDbClient = DynamoDbClient.builder()
            .region(Region.of(region))
            .build();

        this.tableName = System.getenv("DYNAMODB_TABLE_NAME");
        if (this.tableName == null || this.tableName.isBlank()) {
            log.warn("DYNAMODB_TABLE_NAME not set, using default: leetcode-problems-dev");
        }
    }

    private String getTableName() {
        return tableName != null ? tableName : "leetcode-problems-dev";
    }

    /**
     * Get cache metadata for a company and time range
     */
    public Optional<CachedProblemSet> getCacheMetadata(String normalizedCompany, String timeRangeKey) {
        String pk = COMPANY_PREFIX + normalizedCompany;
        String sk = RANGE_PREFIX + timeRangeKey + "#" + METADATA_SUFFIX;

        try {
            GetItemRequest request = GetItemRequest.builder()
                .tableName(getTableName())
                .key(Map.of(
                    "PK", AttributeValue.builder().s(pk).build(),
                    "SK", AttributeValue.builder().s(sk).build()
                ))
                .build();

            GetItemResponse response = dynamoDbClient.getItem(request);

            if (!response.hasItem() || response.item().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(mapToCachedProblemSet(response.item()));

        } catch (Exception e) {
            log.error("Error getting cache metadata: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Save cache metadata
     */
    public void saveCacheMetadata(CachedProblemSet cache) {
        String pk = COMPANY_PREFIX + cache.getCompanyName();
        String sk = RANGE_PREFIX + cache.getTimeRange() + "#" + METADATA_SUFFIX;

        // Calculate TTL (30 days from now by default)
        int expiryDays = Integer.parseInt(System.getenv().getOrDefault("CACHE_EXPIRY_DAYS", "30"));
        long ttl = Instant.now().plusSeconds(expiryDays * 24L * 60 * 60).getEpochSecond();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.builder().s(pk).build());
        item.put("SK", AttributeValue.builder().s(sk).build());
        item.put("companyName", AttributeValue.builder().s(cache.getCompanyName()).build());
        item.put("timeRange", AttributeValue.builder().s(cache.getTimeRange()).build());
        item.put("problemCount", AttributeValue.builder().n(String.valueOf(cache.getProblemCount())).build());
        item.put("lastUpdated", AttributeValue.builder().s(cache.getLastUpdated().toString()).build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
        item.put("entityType", AttributeValue.builder().s("CACHE_METADATA").build());

        // GSI1 keys for alternative access patterns
        String gsi1pk = COMPANY_PREFIX + cache.getCompanyName() + "#" + RANGE_PREFIX + cache.getTimeRange();
        item.put("GSI1PK", AttributeValue.builder().s(gsi1pk).build());
        item.put("GSI1SK", AttributeValue.builder().s(METADATA_SUFFIX).build());

        try {
            PutItemRequest request = PutItemRequest.builder()
                .tableName(getTableName())
                .item(item)
                .build();

            dynamoDbClient.putItem(request);
            log.info("Saved cache metadata for {}/{}", cache.getCompanyName(), cache.getTimeRange());

        } catch (Exception e) {
            log.error("Error saving cache metadata: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save cache metadata", e);
        }
    }

    /**
     * Get all problems for a company and time range
     */
    public List<LeetCodeProblem> getProblems(String normalizedCompany, String timeRangeKey) {
        String pk = COMPANY_PREFIX + normalizedCompany;
        String skPrefix = RANGE_PREFIX + timeRangeKey + "#" + PROBLEM_PREFIX;

        try {
            QueryRequest request = QueryRequest.builder()
                .tableName(getTableName())
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                    ":pk", AttributeValue.builder().s(pk).build(),
                    ":skPrefix", AttributeValue.builder().s(skPrefix).build()
                ))
                .build();

            QueryResponse response = dynamoDbClient.query(request);

            List<LeetCodeProblem> problems = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                problems.add(mapToLeetCodeProblem(item));
            }

            // Sort by frequency descending
            problems.sort((a, b) -> Double.compare(
                b.getFrequency() != null ? b.getFrequency() : 0.0,
                a.getFrequency() != null ? a.getFrequency() : 0.0
            ));

            return problems;

        } catch (Exception e) {
            log.error("Error getting problems: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Save a list of problems for a company and time range
     */
    public void saveProblems(String normalizedCompany, String timeRangeKey, List<LeetCodeProblem> problems) {
        String pk = COMPANY_PREFIX + normalizedCompany;
        String gsi1pk = COMPANY_PREFIX + normalizedCompany + "#" + RANGE_PREFIX + timeRangeKey;

        // Calculate TTL
        int expiryDays = Integer.parseInt(System.getenv().getOrDefault("CACHE_EXPIRY_DAYS", "30"));
        long ttl = Instant.now().plusSeconds(expiryDays * 24L * 60 * 60).getEpochSecond();

        // Batch write (max 25 items per batch in DynamoDB)
        List<WriteRequest> writeRequests = new ArrayList<>();

        for (LeetCodeProblem problem : problems) {
            String sk = RANGE_PREFIX + timeRangeKey + "#" + PROBLEM_PREFIX + problem.getProblemNumber();
            String gsi1sk = PROBLEM_PREFIX + String.format("%06d", problem.getProblemNumber());

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", AttributeValue.builder().s(pk).build());
            item.put("SK", AttributeValue.builder().s(sk).build());
            item.put("GSI1PK", AttributeValue.builder().s(gsi1pk).build());
            item.put("GSI1SK", AttributeValue.builder().s(gsi1sk).build());
            item.put("problemNumber", AttributeValue.builder().n(String.valueOf(problem.getProblemNumber())).build());
            item.put("problemName", AttributeValue.builder().s(problem.getProblemName()).build());
            item.put("acceptanceRate", AttributeValue.builder().n(String.valueOf(problem.getAcceptanceRate())).build());
            item.put("difficulty", AttributeValue.builder().s(problem.getDifficulty().name()).build());
            item.put("frequency", AttributeValue.builder().n(String.valueOf(problem.getFrequency())).build());
            item.put("url", AttributeValue.builder().s(problem.getUrl()).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
            item.put("entityType", AttributeValue.builder().s("PROBLEM").build());

            writeRequests.add(WriteRequest.builder()
                .putRequest(PutRequest.builder().item(item).build())
                .build());

            // Write in batches of 25
            if (writeRequests.size() == 25) {
                batchWrite(writeRequests);
                writeRequests.clear();
            }
        }

        // Write remaining items
        if (!writeRequests.isEmpty()) {
            batchWrite(writeRequests);
        }

        log.info("Saved {} problems for {}/{}", problems.size(), normalizedCompany, timeRangeKey);
    }

    private void batchWrite(List<WriteRequest> writeRequests) {
        try {
            BatchWriteItemRequest request = BatchWriteItemRequest.builder()
                .requestItems(Map.of(getTableName(), writeRequests))
                .build();

            BatchWriteItemResponse response = dynamoDbClient.batchWriteItem(request);

            // Handle unprocessed items (retry logic)
            Map<String, List<WriteRequest>> unprocessed = response.unprocessedItems();
            int retries = 0;
            while (!unprocessed.isEmpty() && retries < 3) {
                log.warn("Retrying {} unprocessed items", unprocessed.size());
                try {
                    Thread.sleep((long) Math.pow(2, retries) * 100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                response = dynamoDbClient.batchWriteItem(
                    BatchWriteItemRequest.builder().requestItems(unprocessed).build()
                );
                unprocessed = response.unprocessedItems();
                retries++;
            }

        } catch (Exception e) {
            log.error("Error batch writing items: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to batch write items", e);
        }
    }

    /**
     * Delete all problems for a company and time range
     */
    public void deleteProblems(String normalizedCompany, String timeRangeKey) {
        String pk = COMPANY_PREFIX + normalizedCompany;
        String skPrefix = RANGE_PREFIX + timeRangeKey + "#";

        try {
            // First query to get all items
            QueryRequest queryRequest = QueryRequest.builder()
                .tableName(getTableName())
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                    ":pk", AttributeValue.builder().s(pk).build(),
                    ":skPrefix", AttributeValue.builder().s(skPrefix).build()
                ))
                .projectionExpression("PK, SK")
                .build();

            QueryResponse response = dynamoDbClient.query(queryRequest);

            // Batch delete
            List<WriteRequest> deleteRequests = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                deleteRequests.add(WriteRequest.builder()
                    .deleteRequest(DeleteRequest.builder()
                        .key(Map.of(
                            "PK", item.get("PK"),
                            "SK", item.get("SK")
                        ))
                        .build())
                    .build());

                if (deleteRequests.size() == 25) {
                    batchWrite(deleteRequests);
                    deleteRequests.clear();
                }
            }

            if (!deleteRequests.isEmpty()) {
                batchWrite(deleteRequests);
            }

            log.info("Deleted problems for {}/{}", normalizedCompany, timeRangeKey);

        } catch (Exception e) {
            log.error("Error deleting problems: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if problems exist for a company and time range
     */
    public boolean problemsExist(String normalizedCompany, String timeRangeKey) {
        return getCacheMetadata(normalizedCompany, timeRangeKey).isPresent();
    }

    /**
     * Get problem count for a company and time range
     */
    public int getProblemCount(String normalizedCompany, String timeRangeKey) {
        return getCacheMetadata(normalizedCompany, timeRangeKey)
            .map(CachedProblemSet::getProblemCount)
            .orElse(0);
    }

    private CachedProblemSet mapToCachedProblemSet(Map<String, AttributeValue> item) {
        return CachedProblemSet.builder()
            .companyName(item.get("companyName").s())
            .timeRange(item.get("timeRange").s())
            .problemCount(Integer.parseInt(item.get("problemCount").n()))
            .lastUpdated(LocalDateTime.parse(item.get("lastUpdated").s()))
            .build();
    }

    private LeetCodeProblem mapToLeetCodeProblem(Map<String, AttributeValue> item) {
        return LeetCodeProblem.builder()
            .problemNumber(Integer.parseInt(item.get("problemNumber").n()))
            .problemName(item.get("problemName").s())
            .acceptanceRate(Double.parseDouble(item.get("acceptanceRate").n()))
            .difficulty(ProblemDifficulty.valueOf(item.get("difficulty").s()))
            .frequency(Double.parseDouble(item.get("frequency").n()))
            .url(item.get("url").s())
            .build();
    }
}
