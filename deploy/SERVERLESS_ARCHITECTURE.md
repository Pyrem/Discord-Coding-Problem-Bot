# Serverless Architecture with AWS Cloud Primitives

Complete re-architecture of the Discord LeetCode Bot using AWS serverless services for massive cost savings and infinite scalability.

## Cost Comparison

### Current EC2 Architecture (~$33/month)
```
EC2 t3.medium:    $30/month
EBS Storage:      $3/month
Total:            ~$33/month
```

### Serverless Architecture (~$8-15/month)
```
Lambda:           $1-2/month (1M free tier)
DynamoDB:         $2-3/month (25GB free tier)
API Gateway:      $0.01/month (1M free tier)
Bedrock:          $4-8/month (pay per token)
EventBridge:      $0/month (free tier covers it)
CloudWatch:       $1-2/month (logs)
Total:            ~$8-15/month
```

**Savings: 55-75% ($18-25/month)**

For low traffic Discord bots (< 10k requests/month), you could hit **$3-5/month** using free tiers!

---

## Architecture Comparison

### Current Architecture (EC2-based)

```
┌─────────────────────────────────────────────┐
│   EC2 Instance (t3.medium - always on)      │
│  ┌────────────────────────────────────────┐ │
│  │ Discord Bot (JDA WebSocket)            │ │
│  │ - Always connected                     │ │
│  │ - Polls for messages                   │ │
│  └────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────┐ │
│  │ PostgreSQL (always running)            │ │
│  └────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────┐ │
│  │ Ollama (2GB RAM always allocated)      │ │
│  └────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘

Cost: $33/month (24/7 regardless of usage)
```

### Proposed Serverless Architecture

```
Discord User
    ↓
┌─────────────────────────────────────────────┐
│ Discord API (sends webhook)                 │
└────────────┬────────────────────────────────┘
             ↓ HTTPS POST
┌─────────────────────────────────────────────┐
│ API Gateway (pay per request)               │
│ - $3.50 per million requests                │
│ - FREE tier: 1M requests/month              │
└────────────┬────────────────────────────────┘
             ↓ Event
┌─────────────────────────────────────────────┐
│ Lambda: DiscordEventHandler                 │
│ - Runs only when message received           │
│ - 1GB RAM, ~2 second execution              │
│ - FREE tier: 1M requests/month              │
│   ├─────────────────────────────────────┐   │
│   │ 1. Parse Discord interaction        │   │
│   │ 2. Validate request                 │   │
│   │ 3. Route to appropriate handler     │   │
│   └─────────────────────────────────────┘   │
└────────────┬────────────────────────────────┘
             ↓
┌─────────────────────────────────────────────┐
│ Lambda: RequestParserFunction               │
│ - Calls Bedrock for NLP                     │
│ - Parses company name + time range          │
│   ├─────────────────────────────────────┐   │
│   │ AWS Bedrock (Claude 3 Haiku)        │   │
│   │ - Pay per token                     │   │
│   │ - ~$0.001 per request               │   │
│   └─────────────────────────────────────┘   │
└────────────┬────────────────────────────────┘
             ↓
┌─────────────────────────────────────────────┐
│ Lambda: ProblemSetFetcher                   │
│ - Checks DynamoDB cache                     │
│ - Fetches from LeetCode API if needed       │
│   ├─────────────────────────────────────┐   │
│   │ DynamoDB: problem_sets_cache        │   │
│   │ - On-demand pricing                 │   │
│   │ - FREE: 25GB storage, 25 RCU/WCU    │   │
│   │ - Automatic scaling                 │   │
│   │ - TTL for auto-expiry               │   │
│   └─────────────────────────────────────┘   │
└────────────┬────────────────────────────────┘
             ↓
┌─────────────────────────────────────────────┐
│ Lambda: DiscordResponseFormatter            │
│ - Formats problems as Discord embeds        │
│ - Returns to API Gateway                    │
└────────────┬────────────────────────────────┘
             ↓ JSON response
┌─────────────────────────────────────────────┐
│ Discord API (displays to user)              │
└─────────────────────────────────────────────┘

Cost: ~$8-15/month (only when used)
Scales: 0 to millions of requests automatically
```

---

## Detailed Component Breakdown

### 1. API Gateway (Discord Webhook Entry Point)

**Purpose**: Receives Discord Interaction webhooks

**Configuration**:
```yaml
Type: HTTP API (cheaper than REST API)
Endpoint: https://your-api-id.execute-api.us-east-1.amazonaws.com/discord
Method: POST
Authorization: None (Discord signature verification in Lambda)
Throttling: 1000 requests/second
```

**Cost**:
- FREE tier: 1M requests/month
- After: $1.00 per million requests
- Expected: $0.01/month for low traffic bot

### 2. Lambda Functions

#### Lambda 1: DiscordEventHandler
**Purpose**: Entry point for all Discord interactions

**Configuration**:
- Runtime: Java 21 or Node.js 20 (faster cold start)
- Memory: 512 MB
- Timeout: 30 seconds
- Environment Variables:
  - `DISCORD_PUBLIC_KEY` (from Parameter Store)
  - `NEXT_LAMBDA_ARN` (for chaining)

**Code Structure**:
```java
public class DiscordEventHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        // 1. Verify Discord signature
        if (!verifyDiscordSignature(event)) {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(401)
                .withBody("{\"error\":\"Invalid signature\"}");
        }

        // 2. Parse interaction
        DiscordInteraction interaction = parseInteraction(event.getBody());

        // 3. Handle PING (required by Discord)
        if (interaction.getType() == 1) {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody("{\"type\":1}");
        }

        // 4. Defer response (Discord requires response within 3 seconds)
        String token = interaction.getToken();
        deferResponse(token);

        // 5. Invoke async processing Lambda
        invokeAsync("RequestParserFunction", interaction);

        // 6. Return acknowledgment
        return new APIGatewayProxyResponseEvent()
            .withStatusCode(200)
            .withBody("{\"type\":5}"); // DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE
    }
}
```

**Cost**:
- FREE tier: 1M requests, 400,000 GB-seconds
- After: $0.20 per 1M requests + $0.0000166667 per GB-second
- Expected: $0.50/month

#### Lambda 2: RequestParserFunction
**Purpose**: Parse natural language using Bedrock

**Configuration**:
- Runtime: Java 21
- Memory: 1024 MB
- Timeout: 60 seconds
- IAM Role: BedrockInvokeModel permission

**Code Structure**:
```java
public class RequestParserFunction implements RequestHandler<DiscordInteraction, CompanyProblemRequest> {

    private final BedrockRuntimeClient bedrockClient;

    public RequestParserFunction() {
        this.bedrockClient = BedrockRuntimeClient.create();
    }

    @Override
    public CompanyProblemRequest handleRequest(DiscordInteraction interaction, Context context) {
        String message = interaction.getData().getOptions().get(0).getValue();

        // Call Bedrock for NLP
        InvokeModelRequest request = InvokeModelRequest.builder()
            .modelId("anthropic.claude-3-haiku-20240307-v1:0")
            .body(SdkBytes.fromUtf8String(createPrompt(message)))
            .build();

        InvokeModelResponse response = bedrockClient.invokeModel(request);
        String responseBody = response.body().asUtf8String();

        // Parse JSON response
        return parseCompanyRequest(responseBody);
    }
}
```

**Cost**:
- Lambda: $1/month
- Bedrock: ~$4-8/month

#### Lambda 3: ProblemSetFetcher
**Purpose**: Fetch problems from cache or LeetCode API

**Configuration**:
- Runtime: Java 21
- Memory: 512 MB
- Timeout: 120 seconds
- VPC: No (public internet access needed)

**Code Structure**:
```java
public class ProblemSetFetcher implements RequestHandler<CompanyProblemRequest, List<LeetCodeProblem>> {

    private final DynamoDbClient dynamoDb;

    @Override
    public List<LeetCodeProblem> handleRequest(CompanyProblemRequest request, Context context) {
        // 1. Check DynamoDB cache
        String cacheKey = request.getCompany() + "_" + request.getTimeRange();
        GetItemResponse cached = dynamoDb.getItem(GetItemRequest.builder()
            .tableName("leetcode_problem_sets")
            .key(Map.of("cache_key", AttributeValue.builder().s(cacheKey).build()))
            .build());

        // 2. If cached and not expired, return
        if (cached.hasItem() && !isExpired(cached.item())) {
            return deserializeProblems(cached.item().get("problems"));
        }

        // 3. Fetch from LeetCode API
        List<LeetCodeProblem> problems = fetchFromLeetCodeAPI(request);

        // 4. Cache in DynamoDB with TTL
        PutItemRequest putRequest = PutItemRequest.builder()
            .tableName("leetcode_problem_sets")
            .item(Map.of(
                "cache_key", AttributeValue.builder().s(cacheKey).build(),
                "problems", serializeProblems(problems),
                "ttl", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis() / 1000 + 30 * 86400)).build()
            ))
            .build();
        dynamoDb.putItem(putRequest);

        return problems;
    }
}
```

**Cost**:
- Lambda: $0.50/month
- DynamoDB: $2-3/month

#### Lambda 4: DiscordResponseFormatter
**Purpose**: Format response and send to Discord

**Configuration**:
- Runtime: Java 21
- Memory: 512 MB
- Timeout: 30 seconds

**Code Structure**:
```java
public class DiscordResponseFormatter implements RequestHandler<List<LeetCodeProblem>, Void> {

    @Override
    public Void handleRequest(List<LeetCodeProblem> problems, Context context) {
        // Format as Discord embeds
        List<DiscordEmbed> embeds = problems.stream()
            .limit(10) // Discord limit
            .map(this::createEmbed)
            .collect(Collectors.toList());

        // Send to Discord via webhook
        String webhookUrl = "https://discord.com/api/webhooks/...";
        sendWebhook(webhookUrl, embeds);

        return null;
    }
}
```

**Cost**: $0.20/month

### 3. DynamoDB (Replaces PostgreSQL)

**Table Design**:

**Table 1: `leetcode_problem_sets`**
```
Partition Key: cache_key (String) - e.g., "microsoft_last30days"
Attributes:
  - problems (List<Map>) - Serialized problem data
  - company (String)
  - time_range (String)
  - problem_count (Number)
  - last_updated (Number) - Unix timestamp
  - ttl (Number) - TTL for auto-expiry (30 days)

Indexes: None needed (simple key-value lookup)
Capacity: On-demand (pay per request)
```

**DynamoDB vs PostgreSQL**:

| Feature | PostgreSQL (EC2) | DynamoDB |
|---------|-----------------|----------|
| **Cost** | ~$3/month (always on) | $2-3/month (on-demand) |
| **Scaling** | Manual | Automatic |
| **Maintenance** | Manual backups, patches | Fully managed |
| **Cold Start** | Always warm | Milliseconds |
| **Best for** | Complex queries | Key-value lookups |

**Cost**:
- FREE tier: 25 GB storage, 25 RCU/WCU
- On-demand: $1.25 per million writes, $0.25 per million reads
- Expected: $2-3/month for low traffic

### 4. AWS Bedrock (Replaces Ollama)

**Model**: Claude 3 Haiku (fastest, cheapest)

**Configuration**:
```
Model ID: anthropic.claude-3-haiku-20240307-v1:0
Region: us-east-1
Max tokens: 500
Temperature: 0.3
```

**Cost**:
- Input: $0.00025 per 1K tokens
- Output: $0.00125 per 1K tokens
- Expected: ~100 tokens per request × 1000 requests = $0.20/month
- Actual with overhead: $4-8/month

### 5. Step Functions (Optional - for complex workflows)

**Purpose**: Orchestrate multi-step processing

**Workflow**:
```
Start
  ↓
Parse Request (Lambda)
  ↓
Check Cache (Lambda)
  ↓
[Cache Miss?] → Fetch from API (Lambda)
  ↓
Format Response (Lambda)
  ↓
Send to Discord (Lambda)
  ↓
End
```

**Cost**: $0.025 per 1,000 state transitions
- Expected: $0.01/month

---

## Migration Strategy

### Phase 1: Convert Discord Bot to Webhook Mode

**Current**: JDA WebSocket (persistent connection)
**New**: Discord Interactions API (webhooks)

**Changes needed**:
1. Register webhook URL with Discord
2. Implement signature verification
3. Use Discord Interactions instead of message events

### Phase 2: Replace PostgreSQL with DynamoDB

**Migration script**:
```java
// Read from PostgreSQL
List<CachedProblemSet> sets = postgresRepo.findAll();

// Write to DynamoDB
for (CachedProblemSet set : sets) {
    dynamoDb.putItem(PutItemRequest.builder()
        .tableName("leetcode_problem_sets")
        .item(Map.of(
            "cache_key", attr(set.getTableName()),
            "problems", attr(set.getProblems()),
            "ttl", attr(set.getLastUpdated().plusDays(30))
        ))
        .build());
}
```

### Phase 3: Replace Ollama with Bedrock

**Code changes**:
```java
// Before (Ollama)
ChatClient chatClient = chatClientBuilder.build();
String response = chatClient.prompt(prompt).call().content();

// After (Bedrock)
BedrockRuntimeClient bedrock = BedrockRuntimeClient.create();
InvokeModelResponse response = bedrock.invokeModel(request);
String content = parseBedrockResponse(response);
```

### Phase 4: Deploy Lambda Functions

**Using SAM (Serverless Application Model)**:
```yaml
# template.yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31

Resources:
  DiscordEventHandler:
    Type: AWS::Serverless::Function
    Properties:
      Runtime: java21
      Handler: com.pyrem.leetcodebot.lambda.DiscordEventHandler
      MemorySize: 512
      Timeout: 30
      Events:
        DiscordWebhook:
          Type: HttpApi
          Properties:
            Path: /discord
            Method: POST

  RequestParserFunction:
    Type: AWS::Serverless::Function
    Properties:
      Runtime: java21
      Handler: com.pyrem.leetcodebot.lambda.RequestParserFunction
      MemorySize: 1024
      Timeout: 60
      Policies:
        - BedrockInvokeModelPolicy

  # ... other functions
```

**Deploy**:
```bash
sam build
sam deploy --guided
```

---

## Implementation Roadmap

### Week 1: Foundation
- [ ] Set up DynamoDB table
- [ ] Create Lambda functions (skeleton)
- [ ] Set up API Gateway
- [ ] Integrate Bedrock

### Week 2: Core Logic
- [ ] Implement Discord webhook handler
- [ ] Implement NLP parser with Bedrock
- [ ] Implement cache lookup in DynamoDB
- [ ] Implement response formatter

### Week 3: Testing & Migration
- [ ] Test end-to-end locally
- [ ] Deploy to AWS
- [ ] Migrate data from PostgreSQL to DynamoDB
- [ ] Run both systems in parallel

### Week 4: Cutover
- [ ] Switch Discord webhook to Lambda endpoint
- [ ] Monitor for issues
- [ ] Decommission EC2 instance
- [ ] 🎉 Celebrate cost savings!

---

## Cost Projection (Detailed)

### Scenario 1: Low Traffic (1,000 requests/month)

```
Lambda:
  - 1,000 invocations × 4 functions = 4,000 invocations
  - Average 2 seconds @ 512 MB = 1,024 GB-seconds
  - FREE TIER COVERS IT = $0

DynamoDB:
  - 1,000 writes + 2,000 reads = 3,000 operations
  - FREE TIER COVERS IT = $0

Bedrock:
  - 1,000 requests × 100 tokens = 100K tokens
  - Input: 50K × $0.00025/1K = $0.01
  - Output: 50K × $0.00125/1K = $0.06
  - Total: $0.07

API Gateway:
  - 1,000 requests
  - FREE TIER COVERS IT = $0

CloudWatch:
  - Logs: ~100 MB = $0.50

TOTAL: ~$0.57/month (98% savings!)
```

### Scenario 2: Medium Traffic (10,000 requests/month)

```
Lambda:
  - 40,000 invocations
  - 10,240 GB-seconds
  - FREE tier: 400,000 GB-seconds
  - STILL FREE = $0

DynamoDB:
  - 10,000 writes: FREE tier covers
  - 20,000 reads: FREE tier covers
  - Total: $0

Bedrock:
  - 10,000 requests × 100 tokens = 1M tokens
  - Input: 500K × $0.00025/1K = $0.12
  - Output: 500K × $0.00125/1K = $0.62
  - Total: $0.74

API Gateway:
  - 10,000 requests
  - FREE TIER COVERS IT = $0

CloudWatch:
  - Logs: ~500 MB = $2

TOTAL: ~$2.74/month (92% savings!)
```

### Scenario 3: High Traffic (100,000 requests/month)

```
Lambda:
  - 400,000 invocations
  - 102,400 GB-seconds
  - After free tier: $0.20 × 0.4M = $0.08
  - GB-seconds: (102,400 - 400,000) × $0 = $0 (still free)
  - Total: $0.08

DynamoDB:
  - 100,000 writes: (100K - 25K) × $1.25/1M = $0.09
  - 200,000 reads: (200K - 25K) × $0.25/1M = $0.04
  - Storage: 5 GB = $1.25
  - Total: $1.38

Bedrock:
  - 100,000 requests × 100 tokens = 10M tokens
  - Input: 5M × $0.00025/1K = $1.25
  - Output: 5M × $0.00125/1K = $6.25
  - Total: $7.50

API Gateway:
  - 100,000 requests (after 1M free) = $0

CloudWatch:
  - Logs: ~2 GB = $5

TOTAL: ~$14/month (58% savings!)
```

---

## Comparison Table

| Metric | EC2 Architecture | Serverless Architecture |
|--------|-----------------|------------------------|
| **Monthly Cost (low traffic)** | $33 | $0.57 (98% ↓) |
| **Monthly Cost (medium traffic)** | $33 | $2.74 (92% ↓) |
| **Monthly Cost (high traffic)** | $33 | $14 (58% ↓) |
| **Cold Start** | None (always on) | 1-3 seconds (Java) |
| **Scaling** | Manual | Automatic |
| **Maintenance** | High | Zero |
| **Uptime SLA** | 99.5% (single AZ) | 99.95% (multi-AZ) |
| **Max Throughput** | ~100 req/sec | ~1,000 req/sec |

---

## Pros and Cons

### ✅ Pros
- **Massive cost savings** (58-98% reduction)
- **Zero maintenance** (no servers to patch)
- **Auto-scaling** (handle traffic spikes)
- **Higher availability** (multi-AZ by default)
- **Pay only for what you use**
- **Better security** (no SSH, no server to compromise)
- **Faster deployments** (just upload Lambda code)

### ❌ Cons
- **Cold starts** (1-3 seconds for Java, ~500ms for Node.js)
  - Mitigation: Use provisioned concurrency ($5/month) or switch to Node.js
- **More complex architecture** (distributed system)
- **Harder local testing** (need SAM local or LocalStack)
- **Discord webhook setup** (vs simple WebSocket)
- **DynamoDB learning curve** (vs familiar SQL)

---

## Recommendation

**For your use case (Discord bot with moderate traffic):**

✅ **Switch to serverless architecture**

**Why:**
- Save $20-30/month (60-90%)
- Better for bursty traffic (Discord bots are usually idle)
- No server maintenance
- Easier to add features (just deploy new Lambda)

**Timeline:**
- Week 1-2: Build serverless version in parallel
- Week 3: Test thoroughly
- Week 4: Cutover and decommission EC2

**Start with:**
1. Move LLM to Bedrock (easiest, immediate savings)
2. Move cache to DynamoDB (second easiest)
3. Convert to Lambda (most work, biggest savings)

Want me to create the actual code for the Lambda functions and SAM template?
