# Discord LeetCode Bot

A serverless Discord bot that processes natural language requests for LeetCode company problem sets, built on AWS Lambda, Bedrock, and DynamoDB.

## Features

- **Natural Language Processing**: Uses AWS Bedrock with Claude 3 Haiku to parse Discord messages
- **Serverless Architecture**: Runs on AWS Lambda with pay-per-use pricing
- **Intelligent Caching**: DynamoDB with TTL-based expiration (30 days)
- **Automatic Time Range Selection**: Finds the most recent problem set with at least 30 problems
- **Rich Discord Embeds**: Beautiful problem displays similar to LeetCode's interface
- **Discord Slash Commands**: Uses Discord Interactions API for reliable webhook-based integration
- **Infrastructure as Code**: AWS SAM template for easy deployment

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         DISCORD                                  │
│  User types: /leetcode query:"Google problems from last 30 days"│
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTPS webhook
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API GATEWAY                                   │
│  POST /discord/interactions                                      │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    LAMBDA (Java 21)                              │
│  • Verify Discord signature                                      │
│  • Parse command with Bedrock                                    │
│  • Query/cache DynamoDB                                          │
│  • Return Discord embed response                                 │
└───────────┬─────────────────────────────────┬───────────────────┘
            │                                 │
            ▼                                 ▼
┌───────────────────────┐         ┌───────────────────────────────┐
│     AWS BEDROCK       │         │         DYNAMODB              │
│  Claude 3 Haiku       │         │  Single-table design          │
│  (NLP parsing)        │         │  TTL-based cache expiration   │
└───────────────────────┘         └───────────────────────────────┘
```

## Cost Estimate

| Component | Monthly Cost (Low Traffic) |
|-----------|---------------------------|
| Lambda | ~$0.50-2 |
| DynamoDB | ~$1-5 |
| Bedrock | ~$0.10-1 |
| **Total** | **~$2-8/month** |

## Prerequisites

- Java 21
- Maven 3.8+
- AWS CLI configured with appropriate credentials
- AWS SAM CLI
- Discord Application with Bot Token

## Quick Start

### 1. Create Discord Application

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application
3. Go to "Bot" section and create a bot
4. Copy the following values:
   - Application ID (General Information)
   - Public Key (General Information)
   - Bot Token (Bot section)

### 2. Set Environment Variables

```bash
export DISCORD_PUBLIC_KEY="your-public-key"
export DISCORD_BOT_TOKEN="your-bot-token"
export DISCORD_APPLICATION_ID="your-application-id"
```

### 3. Build and Deploy

```bash
# Build the project
mvn clean package -DskipTests

# Deploy to AWS
./scripts/deploy.sh dev
```

### 4. Configure Discord Webhook

1. Copy the API Gateway URL from the deployment output
2. Go to Discord Developer Portal → Your Application → General Information
3. Set "Interactions Endpoint URL" to: `https://your-api-gateway-url/dev/discord/interactions`
4. Discord will verify the endpoint (should succeed if deployed correctly)

### 5. Register Slash Commands

```bash
./scripts/register-commands.sh
```

### 6. Invite Bot to Server

1. Go to Discord Developer Portal → Your Application → OAuth2 → URL Generator
2. Select scopes: `bot`, `applications.commands`
3. Select bot permissions: `Send Messages`, `Embed Links`
4. Use the generated URL to invite the bot

## Usage

### Discord Slash Command

```
/leetcode query:Microsoft problems from last 30 days
/leetcode query:Google?
/leetcode query:Amazon and Meta 6 months
```

### Response Format

The bot returns rich embeds showing:
- Problem number and name (clickable link to LeetCode)
- Acceptance rate
- Difficulty level (color-coded: green=Easy, yellow=Medium, red=Hard)
- Frequency visualization (progress bar)

## Project Structure

```
├── pom.xml                          # Maven configuration
├── template.yaml                    # AWS SAM template
├── samconfig.toml                   # SAM deployment config
├── scripts/
│   ├── deploy.sh                    # Deployment script
│   ├── register-commands.sh         # Discord command registration
│   └── local-test.sh                # Local testing
└── src/main/java/com/pyrem/leetcodebot/
    ├── DiscordLambdaHandler.java    # Lambda entry point
    ├── discord/
    │   ├── DiscordSignatureVerifier.java
    │   └── DiscordResponseBuilder.java
    ├── model/
    │   ├── CachedProblemSet.java
    │   ├── CompanyProblemRequest.java
    │   ├── LeetCodeProblem.java
    │   ├── ProblemDifficulty.java
    │   └── TimeRange.java
    ├── nlp/
    │   └── BedrockRequestParser.java
    ├── repository/
    │   └── DynamoDbRepository.java
    └── service/
        ├── LeetCodeService.java
        └── MockLeetCodeClient.java
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DISCORD_PUBLIC_KEY` | Discord application public key | Required |
| `DISCORD_BOT_TOKEN` | Discord bot token | Required |
| `DISCORD_APPLICATION_ID` | Discord application ID | Required |
| `DYNAMODB_TABLE_NAME` | DynamoDB table name | `leetcode-problems-dev` |
| `BEDROCK_MODEL_ID` | Bedrock model ID | `anthropic.claude-3-haiku-20240307-v1:0` |
| `CACHE_EXPIRY_DAYS` | Cache TTL in days | `30` |
| `MIN_PROBLEM_SET_SIZE` | Minimum problems for auto-selection | `30` |
| `MAX_PROBLEM_SET_SIZE` | Maximum problems to return | `50` |

### SAM Template Parameters

| Parameter | Description |
|-----------|-------------|
| `DiscordPublicKey` | Discord public key for signature verification |
| `DiscordBotToken` | Discord bot token |
| `DiscordApplicationId` | Discord application ID |
| `Environment` | Deployment environment (dev/prod) |

## DynamoDB Schema

### Single-Table Design

**Primary Key:**
- PK: `COMPANY#{company}`
- SK: `RANGE#{timeRange}#PROBLEM#{number}` or `RANGE#{timeRange}#METADATA`

**GSI1 (Alternative access):**
- GSI1PK: `COMPANY#{company}#RANGE#{timeRange}`
- GSI1SK: `PROBLEM#{number}` or `METADATA`

**TTL:** Automatic expiration after 30 days

## Local Development

### Testing Locally

```bash
# Run SAM local invoke
./scripts/local-test.sh
```

### Running with DynamoDB Local

```bash
# Start DynamoDB Local
docker run -p 8000:8000 amazon/dynamodb-local

# Set local endpoint
export AWS_ENDPOINT_URL=http://localhost:8000
```

## Deployment Commands

```bash
# Deploy to development
./scripts/deploy.sh dev

# Deploy to production
./scripts/deploy.sh prod

# Validate template
sam validate

# View logs
sam logs -n DiscordInteractionFunction --stack-name discord-leetcode-bot-dev --tail
```

## TODO

- [ ] Implement actual LeetCode API client (replace `MockLeetCodeClient`)
- [ ] Add support for filtering by difficulty
- [ ] Implement pagination for large result sets
- [ ] Add admin commands for cache management
- [ ] Add unit and integration tests
- [ ] Add CloudWatch alarms and monitoring
- [ ] Add X-Ray tracing

## Contributing

This is a personal project. Feel free to fork and customize for your needs.

## License

MIT License
