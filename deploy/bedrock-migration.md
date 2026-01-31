# Migrating to AWS Bedrock

Quick guide to migrate from Ollama to AWS Bedrock for production deployments.

## Why Migrate to Bedrock?

- ✅ **No infrastructure**: No need to manage Ollama containers or GPU instances
- ✅ **Better performance**: 1-2s latency vs 5-10s on CPU
- ✅ **Cost-effective**: ~$5-10/month for typical Discord bot usage
- ✅ **Better accuracy**: Claude 3 Haiku is more capable than llama3.2
- ✅ **Auto-scaling**: Handles traffic spikes automatically

## Prerequisites

1. AWS Account with Bedrock access
2. IAM user with Bedrock permissions
3. Enable Claude models in Bedrock console

## Step 1: Enable Bedrock Models

1. Go to AWS Console → Bedrock → Model access
2. Request access to "Anthropic Claude 3 Haiku"
3. Wait for approval (usually instant)

## Step 2: Update Dependencies

Edit `pom.xml` and add Bedrock dependency:

```xml
<!-- Add to dependencies section -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bedrock-ai-spring-boot-starter</artifactId>
</dependency>

<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>bedrock-runtime</artifactId>
    <version>2.20.0</version>
</dependency>
```

**Note**: You can keep the Ollama dependency for local development, just use different profiles.

## Step 3: Configure AWS Credentials

### Option A: Environment Variables (Recommended for EC2)
```bash
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
```

### Option B: IAM Role (Recommended for ECS/EC2)
If running on EC2 or ECS, attach an IAM role with this policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "bedrock:InvokeModel",
        "bedrock:InvokeModelWithResponseStream"
      ],
      "Resource": [
        "arn:aws:bedrock:*::foundation-model/anthropic.claude-3-haiku-20240307-v1:0"
      ]
    }
  ]
}
```

### Option C: AWS Secrets Manager (Most Secure)
```bash
# Store credentials in Secrets Manager
aws secretsmanager create-secret \
  --name bedrock-credentials \
  --secret-string '{"access_key":"your_key","secret_key":"your_secret"}'
```

## Step 4: Update Application Configuration

The `application-bedrock.properties` file is already created. Just activate it:

```bash
# For local testing
export SPRING_PROFILES_ACTIVE=bedrock
export AWS_REGION=us-east-1

# If not using IAM role, set these too
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key

# Run the application
mvn spring-boot:run
```

## Step 5: Update Docker Deployment

### Option A: Docker Compose with Bedrock (No Ollama)

Create `docker-compose.bedrock.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: leetcode-bot-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: leetcode_bot
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  discord-bot:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: leetcode-bot-app
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      # Use Bedrock profile
      SPRING_PROFILES_ACTIVE: bedrock

      # Database
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/leetcode_bot
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}

      # AWS Credentials
      AWS_REGION: ${AWS_REGION}
      AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID}
      AWS_SECRET_ACCESS_KEY: ${AWS_SECRET_ACCESS_KEY}

      # Discord
      DISCORD_BOT_TOKEN: ${DISCORD_BOT_TOKEN}

      # JVM Settings
      JAVA_OPTS: -Xmx512m -Xms256m

volumes:
  postgres_data:
    driver: local
```

### Option B: ECS Task Definition with Bedrock

Update `deploy/aws-ecs-task-definition.json`:

```json
{
  "family": "discord-leetcode-bot-bedrock",
  "containerDefinitions": [
    {
      "name": "discord-bot",
      "image": "YOUR_ECR_IMAGE",
      "essential": true,
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "bedrock"
        },
        {
          "name": "AWS_REGION",
          "value": "us-east-1"
        }
      ],
      "secrets": [
        {
          "name": "DISCORD_BOT_TOKEN",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:discord-token"
        },
        {
          "name": "SPRING_DATASOURCE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:region:account:secret:db-password"
        }
      ]
    }
  ],
  "taskRoleArn": "arn:aws:iam::account:role/BedrockAccessRole",
  "executionRoleArn": "arn:aws:iam::account:role/ecsTaskExecutionRole"
}
```

**Note**: Remove the Ollama container definition entirely.

## Step 6: Test the Migration

### Local Testing
```bash
# Set environment variables
export SPRING_PROFILES_ACTIVE=bedrock
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your_key
export AWS_SECRET_ACCESS_KEY=your_secret
export DISCORD_BOT_TOKEN=your_token

# Run application
mvn spring-boot:run

# Test in Discord
# Send: "Microsoft?"
# Expected: Bot responds with problem list in ~1-2 seconds
```

### Check Logs
Look for these log messages indicating Bedrock is working:

```
INFO  c.p.l.nlp.RequestParserService - Parsing request: Microsoft?
DEBUG o.s.a.bedrock.anthropic - Calling Bedrock Claude API
INFO  c.p.l.nlp.RequestParserService - LLM Response: {"companies": ["Microsoft"], ...}
```

## Step 7: Deploy to AWS

### EC2 Deployment
```bash
# On EC2 instance
cd ~/discord-bot

# Create .env file
cat > .env << EOF
SPRING_PROFILES_ACTIVE=bedrock
DB_PASSWORD=your_secure_password
DISCORD_BOT_TOKEN=your_discord_token
AWS_REGION=us-east-1
EOF

# If not using IAM role, add:
# AWS_ACCESS_KEY_ID=your_key
# AWS_SECRET_ACCESS_KEY=your_secret

# Use bedrock docker-compose
docker-compose -f docker-compose.bedrock.yml up -d
```

### ECS Deployment
```bash
# Register new task definition
aws ecs register-task-definition \
  --cli-input-json file://deploy/aws-ecs-task-definition-bedrock.json

# Update service
aws ecs update-service \
  --cluster discord-bot-cluster \
  --service discord-bot-service \
  --task-definition discord-leetcode-bot-bedrock:1 \
  --force-new-deployment
```

## Cost Comparison

### Before (Ollama on t3.medium)
```
EC2 t3.medium:  $30/month
EBS Storage:    $3/month
Total:          $33/month
```

### After (Bedrock)
```
EC2 t3.small:   $15/month (smaller instance needed)
EBS Storage:    $2/month
Bedrock usage:  ~$5-10/month (10k requests @ $0.001/request)
Total:          $22-27/month
```

**Savings**: ~$6-11/month + better performance!

## Performance Comparison

| Metric | Ollama (t3.medium) | Bedrock |
|--------|-------------------|---------|
| Latency | 5-10 seconds | 1-2 seconds |
| Accuracy | Good | Excellent |
| Maintenance | Manual | Zero |
| Scaling | Manual | Automatic |

## Rollback Plan

If you need to rollback to Ollama:

```bash
# Change profile
export SPRING_PROFILES_ACTIVE=ec2

# Restart with original docker-compose
docker-compose up -d
```

## Monitoring Bedrock Usage

### View Bedrock Costs
```bash
# AWS Cost Explorer
aws ce get-cost-and-usage \
  --time-period Start=2024-01-01,End=2024-01-31 \
  --granularity MONTHLY \
  --metrics BlendedCost \
  --filter file://bedrock-filter.json
```

### CloudWatch Metrics
Monitor these metrics in CloudWatch:
- `bedrock:ModelInvocations` - Number of API calls
- `bedrock:ModelInvocationLatency` - Response time
- `bedrock:ThrottledRequests` - Rate limit issues

## Troubleshooting

### Issue: "Access Denied" Error
**Solution**: Ensure model access is enabled in Bedrock console and IAM role has permissions.

### Issue: "Model Not Found"
**Solution**: Check that the model ID is correct for your region:
```bash
aws bedrock list-foundation-models --region us-east-1
```

### Issue: Slower than Expected
**Solution**: Claude 3 Haiku should be ~1-2s. If slower:
1. Check network latency to Bedrock region
2. Verify instance has internet connectivity
3. Check CloudWatch metrics for throttling

### Issue: Higher Costs than Expected
**Solution**:
1. Check if you're using Claude Sonnet instead of Haiku
2. Reduce max-tokens in configuration
3. Add caching for repeated questions

## Next Steps

After successful migration:
1. ✅ Remove Ollama from docker-compose to save resources
2. ✅ Set up CloudWatch alarms for cost anomalies
3. ✅ Consider caching LLM responses for common questions
4. ✅ Monitor accuracy and adjust model if needed

## Alternative Models

If Claude 3 Haiku isn't available in your region, try:

```properties
# Llama 3 via Bedrock
spring.ai.bedrock.llama.chat.model=meta.llama3-8b-instruct-v1:0

# Or Anthropic Claude 3 Sonnet (more expensive but better)
spring.ai.bedrock.anthropic.chat.model=anthropic.claude-3-sonnet-20240229-v1:0
```

---

**Total Migration Time**: ~30 minutes

**Difficulty**: Easy (no code changes needed!)
