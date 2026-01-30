# Deployment Prerequisites & Setup Guide

This guide covers all external setup required to deploy the Discord LeetCode Bot, beyond the code in this repository.

## Table of Contents
1. [Discord Bot Setup](#1-discord-bot-setup)
2. [AWS Account Setup](#2-aws-account-setup)
3. [EC2 Deployment Prerequisites](#3-ec2-deployment-prerequisites)
4. [Serverless Lambda Deployment Prerequisites](#4-serverless-lambda-deployment-prerequisites)
5. [Security & Credentials](#5-security--credentials)
6. [Deployment Checklists](#6-deployment-checklists)

---

## 1. Discord Bot Setup

### Step 1.1: Create Discord Application

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Click "New Application"
3. Name it (e.g., "LeetCode Problem Bot")
4. Click "Create"

### Step 1.2: Create Bot User

1. In your application, go to "Bot" tab
2. Click "Add Bot" → "Yes, do it!"
3. **Save the Bot Token** (you'll need this)
   - Click "Reset Token" to see it
   - Copy it immediately (shown only once)
   - Format example: `YOUR_BOT_TOKEN_HERE` (actual tokens are ~70 characters long)

4. Enable these **Privileged Gateway Intents**:
   - ✅ Message Content Intent (required to read messages)

### Step 1.3: Configure Bot Permissions

1. Go to "OAuth2" → "URL Generator"
2. Select scopes:
   - ✅ `bot`
   - ✅ `applications.commands`

3. Select bot permissions:
   - ✅ Send Messages
   - ✅ Embed Links
   - ✅ Read Message History
   - ✅ Use Slash Commands

4. Copy the generated URL
5. Open URL in browser and add bot to your server

### Step 1.4: Get Application Credentials

**For EC2 Deployment (WebSocket):**
- Bot Token: From Step 1.2
- Application ID: Copy from "General Information" tab

**For Lambda Deployment (Webhooks):**
- Bot Token: From Step 1.2
- Application ID: From "General Information" tab
- Public Key: From "General Information" tab (for signature verification)

### Step 1.5: Register Slash Commands (Lambda Only)

You'll need to register slash commands with Discord API:

```bash
# Create a slash command
curl -X POST \
  "https://discord.com/api/v10/applications/YOUR_APPLICATION_ID/commands" \
  -H "Authorization: Bot YOUR_BOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "leetcode",
    "description": "Get LeetCode problems for a company",
    "options": [
      {
        "name": "query",
        "description": "Company name or query (e.g., Microsoft, Google last 30 days)",
        "type": 3,
        "required": true
      }
    ]
  }'
```

---

## 2. AWS Account Setup

### Step 2.1: Create AWS Account

1. Go to [AWS Console](https://aws.amazon.com/)
2. Click "Create an AWS Account"
3. Complete registration
4. Add payment method (required even for free tier)

### Step 2.2: Create IAM User for Deployment

1. Go to IAM Console → Users → Create User
2. Username: `discord-bot-deployer`
3. Enable "Provide user access to the AWS Management Console" (optional)
4. Attach policies:
   - `AdministratorAccess` (for initial setup)
   - Or create custom policy (see below)

**Custom Policy** (more secure):
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:*",
        "iam:*",
        "ssm:*",
        "dynamodb:*",
        "lambda:*",
        "apigateway:*",
        "bedrock:*",
        "logs:*",
        "ecr:*",
        "s3:*",
        "cloudformation:*"
      ],
      "Resource": "*"
    }
  ]
}
```

5. Create access key:
   - IAM → Users → Your User → Security Credentials
   - Click "Create access key"
   - Choose "CLI" use case
   - Download CSV with:
     - Access Key ID: `YOUR_ACCESS_KEY_ID`
     - Secret Access Key: `YOUR_SECRET_ACCESS_KEY`

### Step 2.3: Configure AWS CLI Locally

```bash
# Install AWS CLI
# macOS
brew install awscli

# Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Windows
# Download from: https://awscli.amazonaws.com/AWSCLIV2.msi

# Configure credentials
aws configure
# AWS Access Key ID: YOUR_ACCESS_KEY_ID
# AWS Secret Access Key: YOUR_SECRET_ACCESS_KEY
# Default region name: us-east-1
# Default output format: json
```

### Step 2.4: Enable AWS Bedrock Model Access

1. Go to [AWS Bedrock Console](https://console.aws.amazon.com/bedrock/)
2. Click "Model access" in left sidebar
3. Click "Enable specific models"
4. Find "Anthropic" → Enable "Claude 3 Haiku"
5. Submit request (usually instant approval)
6. Verify status shows "Access granted"

**Important**: Bedrock is not available in all regions. Use:
- ✅ us-east-1 (N. Virginia)
- ✅ us-west-2 (Oregon)
- ✅ eu-central-1 (Frankfurt)

---

## 3. EC2 Deployment Prerequisites

### Step 3.1: Create PostgreSQL Database

**Option A: Local PostgreSQL on EC2** (included in EC2 setup)
- Nothing to do - Docker Compose includes PostgreSQL

**Option B: Amazon RDS** (recommended for production)

1. Go to RDS Console → Create Database
2. Choose:
   - Engine: PostgreSQL 16
   - Template: Free tier (for testing) or Production
   - DB Instance Identifier: `leetcode-bot-db`
   - Master username: `postgres`
   - Master password: (save this!)
   - DB instance class: `db.t4g.micro` (free tier)
   - Storage: 20 GB
   - VPC: Default or create new
   - Public access: No
   - Create database

3. Save connection details:
   - Endpoint: `leetcode-bot-db.abc123.us-east-1.rds.amazonaws.com`
   - Port: `5432`
   - Database name: `leetcode_bot`

### Step 3.2: Create EC2 Instance

1. Go to EC2 Console → Launch Instance
2. Name: `discord-leetcode-bot`
3. AMI: Amazon Linux 2023
4. Instance type: `t3.medium` (2 vCPU, 4 GB RAM)
5. Key pair: Create new or select existing
   - **Download the `.pem` file and save it securely**
6. Network settings:
   - VPC: Default
   - Auto-assign public IP: Enable
   - Security group:
     - SSH (22): Your IP only
     - No other inbound rules needed (bot doesn't expose ports)
7. Storage: 30 GB gp3
8. Advanced:
   - IAM instance profile: Select `DiscordBotEC2Profile` (create via scripts)
9. Launch instance

### Step 3.3: Install Ollama (if using local LLM)

SSH into EC2 and run:

```bash
# Install Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# Pull model
ollama pull llama3.2

# Verify
ollama list
```

### Step 3.4: Create Parameter Store Parameters

Run the setup script:

```bash
cd deploy/scripts
./setup-parameter-store.sh
```

Or manually:

```bash
aws ssm put-parameter \
  --name "/discord-bot/discord-token" \
  --value "YOUR_DISCORD_BOT_TOKEN" \
  --type "SecureString" \
  --region us-east-1

aws ssm put-parameter \
  --name "/discord-bot/db-password" \
  --value "YOUR_SECURE_PASSWORD" \
  --type "SecureString" \
  --region us-east-1

aws ssm put-parameter \
  --name "/discord-bot/db-username" \
  --value "postgres" \
  --type "String" \
  --region us-east-1

aws ssm put-parameter \
  --name "/discord-bot/db-url" \
  --value "jdbc:postgresql://localhost:5432/leetcode_bot" \
  --type "String" \
  --region us-east-1
```

---

## 4. Serverless Lambda Deployment Prerequisites

### Step 4.1: Create DynamoDB Table

```bash
aws dynamodb create-table \
  --table-name leetcode_problem_sets \
  --attribute-definitions \
    AttributeName=cache_key,AttributeType=S \
  --key-schema \
    AttributeName=cache_key,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --tags Key=Application,Value=discord-leetcode-bot \
  --region us-east-1

# Enable TTL for automatic expiration
aws dynamodb update-time-to-live \
  --table-name leetcode_problem_sets \
  --time-to-live-specification \
    "Enabled=true, AttributeName=ttl" \
  --region us-east-1
```

### Step 4.2: Create ECR Repository for Lambda Container

```bash
# Create repository
aws ecr create-repository \
  --repository-name discord-leetcode-bot-lambda \
  --region us-east-1

# Get repository URI (save this)
aws ecr describe-repositories \
  --repository-names discord-leetcode-bot-lambda \
  --query 'repositories[0].repositoryUri' \
  --output text
# Output: 123456789012.dkr.ecr.us-east-1.amazonaws.com/discord-leetcode-bot-lambda
```

### Step 4.3: Build and Push Lambda JAR

```bash
cd lambda/

# Build fat JAR
mvn clean package

# Upload to S3 (create bucket first)
aws s3 mb s3://discord-bot-lambda-deployment-YOUR-ACCOUNT-ID
aws s3 cp target/lambda-functions.jar \
  s3://discord-bot-lambda-deployment-YOUR-ACCOUNT-ID/
```

### Step 4.4: Create Lambda Execution Role

```bash
# Create trust policy
cat > lambda-trust-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Create role
aws iam create-role \
  --role-name DiscordBotLambdaRole \
  --assume-role-policy-document file://lambda-trust-policy.json

# Attach policies
aws iam attach-role-policy \
  --role-name DiscordBotLambdaRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam attach-role-policy \
  --role-name DiscordBotLambdaRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess

# Create custom policy for Bedrock
cat > bedrock-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "bedrock:InvokeModel",
        "bedrock:InvokeModelWithResponseStream"
      ],
      "Resource": "arn:aws:bedrock:*::foundation-model/anthropic.claude-3-haiku-20240307-v1:0"
    },
    {
      "Effect": "Allow",
      "Action": "lambda:InvokeFunction",
      "Resource": "arn:aws:lambda:*:*:function:*"
    }
  ]
}
EOF

aws iam put-role-policy \
  --role-name DiscordBotLambdaRole \
  --policy-name BedrockAndLambdaAccess \
  --policy-document file://bedrock-policy.json
```

### Step 4.5: Create Lambda Functions

```bash
# Get role ARN
ROLE_ARN=$(aws iam get-role --role-name DiscordBotLambdaRole --query 'Role.Arn' --output text)

# Create Lambda 1: DiscordEventHandler
aws lambda create-function \
  --function-name DiscordEventHandler \
  --runtime java21 \
  --role $ROLE_ARN \
  --handler com.pyrem.leetcodebot.lambda.DiscordEventHandler \
  --code S3Bucket=discord-bot-lambda-deployment-YOUR-ACCOUNT-ID,S3Key=lambda-functions.jar \
  --memory-size 512 \
  --timeout 30 \
  --environment Variables={REQUEST_PARSER_FUNCTION=RequestParserFunction} \
  --region us-east-1

# Create Lambda 2: RequestParserFunction
aws lambda create-function \
  --function-name RequestParserFunction \
  --runtime java21 \
  --role $ROLE_ARN \
  --handler com.pyrem.leetcodebot.lambda.RequestParserFunction \
  --code S3Bucket=discord-bot-lambda-deployment-YOUR-ACCOUNT-ID,S3Key=lambda-functions.jar \
  --memory-size 1024 \
  --timeout 60 \
  --environment Variables={PROBLEM_FETCHER_FUNCTION=ProblemFetcherFunction} \
  --region us-east-1

# Create Lambda 3: ProblemFetcherFunction
aws lambda create-function \
  --function-name ProblemFetcherFunction \
  --runtime java21 \
  --role $ROLE_ARN \
  --handler com.pyrem.leetcodebot.lambda.ProblemFetcherFunction \
  --code S3Bucket=discord-bot-lambda-deployment-YOUR-ACCOUNT-ID,S3Key=lambda-functions.jar \
  --memory-size 512 \
  --timeout 120 \
  --environment Variables={DYNAMODB_TABLE=leetcode_problem_sets,RESPONSE_FORMATTER_FUNCTION=DiscordResponseFormatter} \
  --region us-east-1
```

### Step 4.6: Create API Gateway

```bash
# Create HTTP API
aws apigatewayv2 create-api \
  --name discord-bot-api \
  --protocol-type HTTP \
  --target arn:aws:lambda:us-east-1:YOUR_ACCOUNT_ID:function:DiscordEventHandler \
  --region us-east-1

# Get API endpoint (save this)
aws apigatewayv2 get-apis \
  --query 'Items[?Name==`discord-bot-api`].ApiEndpoint' \
  --output text
# Output: https://abc123xyz.execute-api.us-east-1.amazonaws.com
```

### Step 4.7: Configure Discord Webhook URL

1. Go to Discord Developer Portal
2. Your Application → General Information
3. Interactions Endpoint URL: `https://abc123xyz.execute-api.us-east-1.amazonaws.com/discord`
4. Click "Save Changes"
5. Discord will send a PING to verify (Lambda handles this automatically)

---

## 5. Security & Credentials

### Credentials You Need to Keep Secure

| Credential | Where to Get | Where to Store | Never Commit |
|------------|--------------|----------------|--------------|
| **Discord Bot Token** | Discord Developer Portal | Parameter Store / .env | ❌ |
| **Discord Public Key** | Discord Developer Portal | Parameter Store | ✅ OK |
| **AWS Access Key ID** | IAM Console | `~/.aws/credentials` | ❌ |
| **AWS Secret Access Key** | IAM Console | `~/.aws/credentials` | ❌ |
| **Database Password** | You create it | Parameter Store / .env | ❌ |
| **EC2 SSH Key (.pem)** | EC2 Launch | Local filesystem | ❌ |

### How to Store Credentials

**For EC2 Deployment:**
```bash
# Store in AWS Parameter Store
./deploy/scripts/setup-parameter-store.sh
```

**For Local Development:**
```bash
# Create .env file (already in .gitignore)
cp .env.example .env
# Edit .env with your actual credentials
```

**For Lambda Deployment:**
- Stored in Lambda environment variables
- Or use Parameter Store with Lambda extension layer

---

## 6. Deployment Checklists

### Option A: EC2 Deployment Checklist

**Prerequisites:**
- [ ] Discord bot created and bot token saved
- [ ] AWS account created
- [ ] AWS CLI configured locally
- [ ] EC2 instance launched with IAM role
- [ ] SSH key pair downloaded and secured (`chmod 400 your-key.pem`)
- [ ] Ollama installed on EC2 (if not using Bedrock)
- [ ] Parameter Store parameters created

**Deployment Steps:**
1. [ ] Run `./deploy/scripts/setup-parameter-store.sh` (store credentials)
2. [ ] Run `./deploy/scripts/setup-iam-role.sh` (create IAM role)
3. [ ] Launch EC2 instance with `DiscordBotEC2Profile`
4. [ ] SSH into EC2: `ssh -i your-key.pem ec2-user@YOUR-EC2-IP`
5. [ ] Install Docker and Docker Compose (see AWS_DEPLOYMENT_GUIDE.md)
6. [ ] Clone repository or copy files to EC2
7. [ ] Build and push Docker image to ECR (or use local Docker)
8. [ ] Set up docker-compose.yml with correct environment variables
9. [ ] Run `docker-compose up -d`
10. [ ] Pull Ollama model: `docker exec ollama ollama pull llama3.2`
11. [ ] Test in Discord: send a message mentioning the bot
12. [ ] Verify logs: `docker-compose logs -f discord-bot`

**Cost Estimate:** ~$30-35/month

---

### Option B: Serverless Lambda Deployment Checklist

**Prerequisites:**
- [ ] Discord bot created with bot token and public key
- [ ] AWS account created
- [ ] AWS CLI configured locally
- [ ] Bedrock model access enabled (Claude 3 Haiku)
- [ ] DynamoDB table created
- [ ] Lambda execution role created with permissions

**Deployment Steps:**
1. [ ] Build Lambda JAR: `cd lambda && mvn clean package`
2. [ ] Create S3 bucket for deployment artifacts
3. [ ] Upload JAR to S3
4. [ ] Create 3 Lambda functions (DiscordEventHandler, RequestParserFunction, ProblemFetcherFunction)
5. [ ] Create API Gateway HTTP API
6. [ ] Point API Gateway to DiscordEventHandler Lambda
7. [ ] Get API Gateway endpoint URL
8. [ ] Configure Discord webhook URL in Discord Developer Portal
9. [ ] Discord will verify with PING (check Lambda logs)
10. [ ] Register slash command with Discord API
11. [ ] Test in Discord: `/leetcode Microsoft`
12. [ ] Monitor CloudWatch Logs for errors

**Cost Estimate:** ~$8-15/month (or $0.57/month for low traffic with free tier)

---

## 7. Post-Deployment Verification

### Verify EC2 Deployment

```bash
# SSH into EC2
ssh -i your-key.pem ec2-user@YOUR-EC2-IP

# Check containers are running
docker ps
# Should see: discord-bot, postgres, ollama

# Check logs
docker-compose logs -f discord-bot

# Expected output:
# "Discord bot initialized successfully!"
# "Logged in as: YourBotName"

# Test Parameter Store access
aws ssm get-parameter \
  --name /discord-bot/discord-token \
  --with-decryption \
  --region us-east-1
```

### Verify Lambda Deployment

```bash
# Test Lambda directly
aws lambda invoke \
  --function-name DiscordEventHandler \
  --payload '{"body":"{\"type\":1}"}' \
  --region us-east-1 \
  response.json

cat response.json
# Should see: {"statusCode":200,"body":"{\"type\":1}"}

# Check CloudWatch Logs
aws logs tail /aws/lambda/DiscordEventHandler --follow

# Test API Gateway
curl -X POST https://YOUR-API-GATEWAY-URL/discord \
  -H "Content-Type: application/json" \
  -d '{"type":1}'
# Should return: {"type":1}
```

### Verify Discord Integration

1. Open Discord and go to your test server
2. Send a message: `@YourBot Microsoft?`
3. Bot should respond with LeetCode problems
4. Check response time:
   - EC2: ~2-3 seconds
   - Lambda (cold): ~5-7 seconds
   - Lambda (warm): ~2-3 seconds

---

## 8. Troubleshooting

### Common Issues

**Issue: Bot not responding in Discord**
```bash
# Check if bot is online (EC2)
docker ps | grep discord-bot

# Check logs for errors
docker-compose logs discord-bot | tail -50

# Verify bot token
aws ssm get-parameter --name /discord-bot/discord-token --with-decryption
```

**Issue: Lambda returns 401 from Discord**
- Discord signature verification failed
- Ensure PUBLIC_KEY is correct in Lambda environment
- Check CloudWatch Logs for "Invalid signature" errors

**Issue: Bedrock AccessDeniedException**
```bash
# Verify model access is enabled
aws bedrock list-foundation-models --region us-east-1 | grep claude-3-haiku

# Check IAM role has bedrock:InvokeModel permission
aws iam get-role-policy \
  --role-name DiscordBotLambdaRole \
  --policy-name BedrockAndLambdaAccess
```

**Issue: DynamoDB access denied**
```bash
# Verify table exists
aws dynamodb describe-table --table-name leetcode_problem_sets

# Check IAM role permissions
aws iam list-attached-role-policies --role-name DiscordBotLambdaRole
```

---

## 9. Cost Breakdown

### EC2 Deployment Costs

```
EC2 t3.medium:        $30/month
EBS Storage (30 GB):  $3/month
Data Transfer:        $1-2/month
---------------------------------
Total:                ~$33-35/month
```

### Serverless Deployment Costs

**Low Traffic (1,000 requests/month):**
```
Lambda:               $0 (free tier)
DynamoDB:             $0 (free tier)
Bedrock:              $0.10
API Gateway:          $0 (free tier)
CloudWatch Logs:      $0.50
---------------------------------
Total:                ~$0.60/month
```

**Medium Traffic (10,000 requests/month):**
```
Lambda:               $1
DynamoDB:             $2
Bedrock:              $1
API Gateway:          $0.01
CloudWatch Logs:      $2
---------------------------------
Total:                ~$6/month
```

---

## 10. Next Steps After Deployment

1. [ ] Set up monitoring:
   - CloudWatch alarms for Lambda errors
   - CloudWatch dashboard for metrics
   - SNS notifications for failures

2. [ ] Configure auto-scaling (if using EC2):
   - Auto Scaling Group
   - Application Load Balancer
   - Health checks

3. [ ] Set up CI/CD:
   - GitHub Actions for automated testing
   - AWS CodePipeline for deployment
   - Automated rollbacks on failure

4. [ ] Implement actual LeetCode API:
   - Replace MockLeetCodeClient
   - Add rate limiting
   - Handle API authentication

5. [ ] Add features:
   - Filter by difficulty
   - Pagination for large result sets
   - Admin commands
   - User preferences

---

## Summary

**Minimum Required:**
1. Discord bot token ✅
2. AWS account ✅
3. AWS CLI configured ✅
4. Bedrock model access (for serverless) ✅
5. 30 minutes of setup time ✅

**Total Time to Deploy:**
- EC2: ~1 hour
- Lambda: ~2 hours (more setup)

**Recommendation:**
Start with **EC2 deployment** - it's simpler and you can migrate to Lambda later if needed!
