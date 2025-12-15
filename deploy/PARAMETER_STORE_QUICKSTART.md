# AWS Parameter Store - Quick Start Guide

Get your Discord bot running on EC2 with secure credential management in under 30 minutes.

## Overview

This guide uses **AWS Systems Manager Parameter Store** to securely store:
- Discord bot token
- PostgreSQL credentials
- Other configuration values

**Benefits**: Free, encrypted, auditable, and no credentials in code or `.env` files!

---

## Prerequisites

- AWS CLI configured with admin credentials
- EC2 instance (or plan to launch one)
- Discord bot token
- 20 minutes

---

## Step 1: Store Credentials in Parameter Store (5 minutes)

Run the automated setup script:

```bash
cd deploy/scripts
./setup-parameter-store.sh
```

**What it asks for:**
- Discord Bot Token
- PostgreSQL Password
- PostgreSQL Username (default: postgres)
- Database URL (default: jdbc:postgresql://localhost:5432/leetcode_bot)

**What it creates:**
- `/discord-bot/discord-token` (SecureString, encrypted)
- `/discord-bot/db-password` (SecureString, encrypted)
- `/discord-bot/db-username` (String)
- `/discord-bot/db-url` (String)
- `/discord-bot/ollama-url` (String)

**Verify:**
```bash
# List all parameters
aws ssm get-parameters-by-path --path /discord-bot --region us-east-1

# View specific parameter (with decryption)
aws ssm get-parameter \
  --name /discord-bot/discord-token \
  --with-decryption \
  --region us-east-1
```

---

## Step 2: Create IAM Role for EC2 (5 minutes)

Run the IAM setup script:

```bash
cd deploy/scripts
./setup-iam-role.sh
```

**What it creates:**
- IAM Role: `DiscordBotEC2Role`
- IAM Policy: `DiscordBotParameterStoreAccess`
- Instance Profile: `DiscordBotEC2Profile`

**Manual alternative:**
```bash
# Create role
aws iam create-role \
  --role-name DiscordBotEC2Role \
  --assume-role-policy-document file://../iam-policies/ec2-trust-policy.json

# Create and attach policy
aws iam create-policy \
  --policy-name DiscordBotParameterStoreAccess \
  --policy-document file://../iam-policies/parameter-store-access-policy.json

POLICY_ARN=$(aws iam list-policies --query "Policies[?PolicyName=='DiscordBotParameterStoreAccess'].Arn" --output text)

aws iam attach-role-policy \
  --role-name DiscordBotEC2Role \
  --policy-arn $POLICY_ARN

# Create instance profile
aws iam create-instance-profile --instance-profile-name DiscordBotEC2Profile
aws iam add-role-to-instance-profile \
  --instance-profile-name DiscordBotEC2Profile \
  --role-name DiscordBotEC2Role
```

---

## Step 3: Launch EC2 Instance with IAM Role (10 minutes)

### Option A: New Instance

```bash
aws ec2 run-instances \
  --image-id ami-0c55b159cbfafe1f0 \
  --instance-type t3.medium \
  --iam-instance-profile Name=DiscordBotEC2Profile \
  --key-name your-key-pair \
  --security-group-ids sg-your-security-group \
  --subnet-id subnet-your-subnet \
  --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=discord-leetcode-bot}]' \
  --user-data file://user-data.sh
```

### Option B: Existing Instance

```bash
# Get your instance ID
INSTANCE_ID=i-1234567890abcdef0

# Attach IAM role
aws ec2 associate-iam-instance-profile \
  --instance-id $INSTANCE_ID \
  --iam-instance-profile Name=DiscordBotEC2Profile

# Restart instance for changes to take effect
aws ec2 reboot-instances --instance-ids $INSTANCE_ID
```

---

## Step 4: Setup EC2 Instance (5 minutes)

SSH into your EC2 instance and install Docker:

```bash
# SSH into instance
ssh -i your-key.pem ec2-user@your-instance-ip

# Update system
sudo yum update -y

# Install Docker
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -a -G docker ec2-user

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Install AWS CLI (if not already installed)
sudo yum install -y aws-cli

# Create data directories
sudo mkdir -p /data/postgres /data/ollama
sudo chown -R 999:999 /data/postgres
sudo chown -R ec2-user:ec2-user /data/ollama

# Log out and back in for Docker group to take effect
exit
```

---

## Step 5: Test Parameter Store Access (2 minutes)

SSH back in and test:

```bash
ssh -i your-key.pem ec2-user@your-instance-ip

# Download test script
curl -o test-parameter-store.sh \
  https://raw.githubusercontent.com/YOUR_REPO/main/deploy/scripts/test-parameter-store.sh
chmod +x test-parameter-store.sh

# Run tests
./test-parameter-store.sh
```

**Expected output:**
```
✅ PASSED: IAM role attached
✅ PASSED: Found parameters
✅ PASSED: Retrieved discord-token
✅ PASSED: Successfully decrypted
✅ PASSED: All required parameters exist
=== All Tests Passed! ===
```

---

## Step 6: Deploy Application (5 minutes)

### Setup application directory:

```bash
mkdir -p ~/discord-bot
cd ~/discord-bot

# Authenticate to ECR
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Create docker-compose.yml
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: leetcode_bot
      POSTGRES_USER: postgres
      # Password will be set by entrypoint script
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    volumes:
      - /data/postgres:/var/lib/postgresql/data
    secrets:
      - db_password

  ollama:
    image: ollama/ollama:latest
    restart: unless-stopped
    volumes:
      - /data/ollama:/root/.ollama
    ports:
      - "11434:11434"

  discord-bot:
    image: ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/discord-leetcode-bot:latest
    restart: unless-stopped
    depends_on:
      - postgres
      - ollama
    environment:
      # Use Parameter Store profile
      SPRING_PROFILES_ACTIVE: ec2-paramstore
      AWS_REGION: us-east-1
      # No secrets needed - Spring Boot fetches from Parameter Store!

secrets:
  db_password:
    file: /tmp/db_password

EOF

# Create entrypoint script that fetches DB password
cat > fetch-db-password.sh << 'EOF'
#!/bin/bash
aws ssm get-parameter \
  --name /discord-bot/db-password \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region us-east-1 > /tmp/db_password
EOF
chmod +x fetch-db-password.sh

# Pull images
docker-compose pull

# Fetch DB password and start services
./fetch-db-password.sh
docker-compose up -d

# Pull Ollama model
docker exec discord-bot-ollama ollama pull llama3.2

# Check logs
docker-compose logs -f discord-bot
```

---

## Step 7: Verify Deployment (2 minutes)

### Check application logs:

```bash
docker-compose logs discord-bot | grep "Parameter Store"
```

**Expected:**
```
INFO  io.awspring.cloud.parameterstore - Loading properties from AWS Parameter Store
INFO  io.awspring.cloud.parameterstore - Successfully loaded 5 parameters
INFO  c.p.l.DiscordLeetCodeBotApplication - Discord bot initialized successfully!
```

### Test in Discord:

Send a message in your Discord server:
```
Microsoft?
```

Bot should respond with a list of Microsoft LeetCode problems!

---

## Architecture Diagram

```
┌─────────────────────────────────────────┐
│   Your Local Machine                    │
│  ┌───────────────────────────────────┐  │
│  │ 1. Run setup-parameter-store.sh   │  │
│  │ 2. Run setup-iam-role.sh          │  │
│  └───────────────────────────────────┘  │
└────────────────┬────────────────────────┘
                 │ AWS CLI
                 ↓
┌─────────────────────────────────────────┐
│   AWS Systems Manager                   │
│  ┌───────────────────────────────────┐  │
│  │ Parameter Store                   │  │
│  │  - /discord-bot/discord-token     │  │
│  │  - /discord-bot/db-password       │  │
│  │  - /discord-bot/db-username       │  │
│  │  - /discord-bot/db-url            │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
                 ↑
                 │ IAM Role (GetParameter)
                 │
┌─────────────────────────────────────────┐
│   EC2 Instance                          │
│  ┌───────────────────────────────────┐  │
│  │ IAM Role: DiscordBotEC2Role       │  │
│  │                                   │  │
│  │ Spring Boot App                   │  │
│  │  - Reads from Parameter Store     │  │
│  │  - No credentials in code!        │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ PostgreSQL Container              │  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │ Ollama Container                  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

---

## Troubleshooting

### Issue: "AccessDeniedException" when accessing parameters

**Solution:**
```bash
# Verify IAM role is attached
aws ec2 describe-instances --instance-ids i-xxx \
  --query 'Reservations[0].Instances[0].IamInstanceProfile'

# If not attached, attach it
aws ec2 associate-iam-instance-profile \
  --instance-id i-xxx \
  --iam-instance-profile Name=DiscordBotEC2Profile
```

### Issue: "ParameterNotFound"

**Solution:**
```bash
# Check if parameters exist
aws ssm get-parameters-by-path --path /discord-bot --region us-east-1

# If missing, run setup script again
cd deploy/scripts
./setup-parameter-store.sh
```

### Issue: Spring Boot not loading parameters

**Solution:**
```bash
# Check application logs for Spring Cloud AWS
docker-compose logs discord-bot | grep parameterstore

# Verify profile is set correctly
docker-compose exec discord-bot env | grep SPRING_PROFILES_ACTIVE
# Should show: ec2-paramstore
```

---

## Cost Breakdown

**Parameter Store:**
- Standard parameters: **FREE** (up to 10,000)
- API calls: **FREE** (standard throughput)

**EC2 Instance (t3.medium):**
- ~$30/month

**Total new costs:** **$0** (just using existing EC2)

**Security improvement:** ⭐⭐⭐⭐⭐

---

## Next Steps

1. ✅ Set up CloudTrail to audit parameter access
2. ✅ Enable automatic credential rotation
3. ✅ Add CloudWatch alarms for failed parameter retrievals
4. ✅ Create separate parameters for dev/staging/prod environments

---

## Comparison: Before vs After

### Before (using .env files)

```bash
# .env file on EC2
DISCORD_BOT_TOKEN=MTIzNDU2...  # ❌ Visible in filesystem
DB_PASSWORD=mysecretpass        # ❌ Not encrypted
```

**Problems:**
- ❌ Credentials visible to anyone with EC2 access
- ❌ No encryption at rest
- ❌ No audit trail
- ❌ Hard to rotate credentials
- ❌ Credentials in git history (if committed)

### After (using Parameter Store)

```bash
# application-ec2-paramstore.properties
discord.bot.token=${discord-token}  # ✅ Fetched at runtime
spring.datasource.password=${db-password}  # ✅ Encrypted in transit
```

**Benefits:**
- ✅ Credentials never on filesystem
- ✅ Encrypted at rest with KMS
- ✅ Full audit trail in CloudTrail
- ✅ Easy credential rotation
- ✅ IAM-based access control

---

## Summary

**Total setup time:** ~30 minutes

**Files created:**
- 5 parameters in Parameter Store (free)
- 1 IAM role with policy
- 1 EC2 instance profile

**Security level:** Production-ready ⭐⭐⭐⭐⭐

**Maintenance:** Zero - AWS handles everything!
