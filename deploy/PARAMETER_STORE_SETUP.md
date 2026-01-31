# AWS Systems Manager Parameter Store Integration

This guide shows how to use AWS Systems Manager Parameter Store to securely manage credentials on EC2.

## Benefits

- ✅ **Free**: Standard parameters are free (up to 10,000)
- ✅ **Secure**: Encrypted at rest with AWS KMS
- ✅ **Auditable**: CloudTrail logs all access
- ✅ **Centralized**: Manage all credentials in one place
- ✅ **No code changes**: Spring Boot reads directly from Parameter Store
- ✅ **Versioned**: Track changes to credentials over time

## Architecture

```
┌─────────────────────────────────────────┐
│   EC2 Instance (Discord Bot)            │
│  ┌───────────────────────────────────┐  │
│  │ Spring Boot Application           │  │
│  │  - Has IAM Role attached          │  │
│  │  - Reads from Parameter Store     │  │
│  └───────────────────────────────────┘  │
└────────────────┬────────────────────────┘
                 │ AWS SDK (IAM Role)
                 ↓
┌─────────────────────────────────────────┐
│   AWS Systems Manager Parameter Store   │
│  ┌───────────────────────────────────┐  │
│  │ /discord-bot/discord-token        │  │
│  │ /discord-bot/db-password          │  │
│  │ /discord-bot/db-username          │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

## Step 1: Store Credentials in Parameter Store

### Using AWS CLI

```bash
# Set your AWS region
export AWS_REGION=us-east-1

# Store Discord bot token (encrypted)
aws ssm put-parameter \
  --name "/discord-bot/discord-token" \
  --value "your_discord_bot_token_here" \
  --type "SecureString" \
  --description "Discord bot authentication token" \
  --tags "Key=Application,Value=discord-leetcode-bot" "Key=Environment,Value=production"

# Store database password (encrypted)
aws ssm put-parameter \
  --name "/discord-bot/db-password" \
  --value "your_secure_db_password" \
  --type "SecureString" \
  --description "PostgreSQL database password"

# Store database username (can be String instead of SecureString)
aws ssm put-parameter \
  --name "/discord-bot/db-username" \
  --value "postgres" \
  --type "String" \
  --description "PostgreSQL database username"

# Optional: Store database URL
aws ssm put-parameter \
  --name "/discord-bot/db-url" \
  --value "jdbc:postgresql://localhost:5432/leetcode_bot" \
  --type "String" \
  --description "PostgreSQL database connection URL"
```

### Using AWS Console

1. Go to AWS Console → Systems Manager → Parameter Store
2. Click "Create parameter"
3. Name: `/discord-bot/discord-token`
4. Type: `SecureString`
5. Value: Your Discord bot token
6. Click "Create parameter"
7. Repeat for other credentials

## Step 2: Create IAM Role for EC2

### Policy for Parameter Store Access

Create `parameter-store-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ssm:GetParameter",
        "ssm:GetParameters",
        "ssm:GetParametersByPath"
      ],
      "Resource": [
        "arn:aws:ssm:*:*:parameter/discord-bot/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "kms:Decrypt"
      ],
      "Resource": [
        "arn:aws:kms:*:*:key/*"
      ],
      "Condition": {
        "StringEquals": {
          "kms:ViaService": [
            "ssm.us-east-1.amazonaws.com"
          ]
        }
      }
    }
  ]
}
```

### Create IAM Role

```bash
# Create trust policy for EC2
cat > trust-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

# Create IAM role
aws iam create-role \
  --role-name DiscordBotEC2Role \
  --assume-role-policy-document file://trust-policy.json

# Create parameter store access policy
aws iam create-policy \
  --policy-name DiscordBotParameterStoreAccess \
  --policy-document file://parameter-store-policy.json

# Attach policy to role
aws iam attach-role-policy \
  --role-name DiscordBotEC2Role \
  --policy-arn arn:aws:iam::YOUR_ACCOUNT_ID:policy/DiscordBotParameterStoreAccess

# Create instance profile
aws iam create-instance-profile \
  --instance-profile-name DiscordBotEC2Profile

# Add role to instance profile
aws iam add-role-to-instance-profile \
  --instance-profile-name DiscordBotEC2Profile \
  --role-name DiscordBotEC2Role
```

## Step 3: Attach IAM Role to EC2 Instance

### For New Instance

```bash
aws ec2 run-instances \
  --image-id ami-0c55b159cbfafe1f0 \
  --instance-type t3.medium \
  --iam-instance-profile Name=DiscordBotEC2Profile \
  --key-name your-key-pair \
  --security-group-ids sg-your-sg \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=discord-leetcode-bot}]'
```

### For Existing Instance

```bash
# Get instance ID
INSTANCE_ID=i-1234567890abcdef0

# Associate IAM role
aws ec2 associate-iam-instance-profile \
  --instance-id $INSTANCE_ID \
  --iam-instance-profile Name=DiscordBotEC2Profile
```

## Step 4: Update Application Configuration

Spring Boot can automatically read from Parameter Store using Spring Cloud AWS.

### Add Dependency to pom.xml

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-parameter-store</artifactId>
    <version>3.1.0</version>
</dependency>
```

### Update application-ec2.properties

```properties
# Enable AWS Parameter Store integration
spring.cloud.aws.parameterstore.enabled=true
spring.cloud.aws.parameterstore.prefix=/discord-bot
spring.cloud.aws.region.static=${AWS_REGION:us-east-1}

# Reference parameters using ${} syntax
# Spring will automatically fetch from Parameter Store
discord.bot.token=${discord-token}

# Database configuration
spring.datasource.url=${db-url:jdbc:postgresql://localhost:5432/leetcode_bot}
spring.datasource.username=${db-username:postgres}
spring.datasource.password=${db-password}

# Ollama configuration
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2

# Logging
logging.level.com.pyrem.leetcodebot=INFO
logging.level.io.awspring.cloud.parameterstore=DEBUG
```

### Alternative: Manual Parameter Fetching

If you prefer more control, you can fetch parameters manually:

```java
@Configuration
public class AwsParameterStoreConfig {

    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }

    @Bean
    public String discordBotToken(SsmClient ssmClient) {
        GetParameterRequest request = GetParameterRequest.builder()
            .name("/discord-bot/discord-token")
            .withDecryption(true)
            .build();

        GetParameterResponse response = ssmClient.getParameter(request);
        return response.parameter().value();
    }
}
```

## Step 5: Update Docker Deployment

### Update docker-compose.yml for EC2

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: leetcode_bot
      # Read from environment variable set by startup script
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - /data/postgres:/var/lib/postgresql/data

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
      SPRING_PROFILES_ACTIVE: ec2
      AWS_REGION: ${AWS_REGION}
      # No need to pass secrets - Spring Boot will fetch from Parameter Store
    # IAM role attached to EC2 instance provides credentials
```

### Create Startup Script

Create `/usr/local/bin/load-parameters.sh`:

```bash
#!/bin/bash
set -e

# Fetch parameters from Parameter Store
export DB_USERNAME=$(aws ssm get-parameter --name "/discord-bot/db-username" --query "Parameter.Value" --output text --region us-east-1)
export DB_PASSWORD=$(aws ssm get-parameter --name "/discord-bot/db-password" --with-decryption --query "Parameter.Value" --output text --region us-east-1)
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Start Docker Compose
cd /home/ec2-user/discord-bot
docker-compose up -d
```

Make it executable:
```bash
chmod +x /usr/local/bin/load-parameters.sh
```

### Update systemd Service

```bash
sudo tee /etc/systemd/system/discord-bot.service > /dev/null << 'EOF'
[Unit]
Description=Discord LeetCode Bot
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/local/bin/load-parameters.sh
ExecStop=/usr/local/bin/docker-compose -f /home/ec2-user/discord-bot/docker-compose.yml down
User=ec2-user
WorkingDirectory=/home/ec2-user/discord-bot

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable discord-bot.service
```

## Step 6: Verify Setup

### Test Parameter Access

```bash
# SSH into EC2 instance
ssh -i your-key.pem ec2-user@your-instance-ip

# Test parameter retrieval
aws ssm get-parameter \
  --name "/discord-bot/discord-token" \
  --with-decryption \
  --region us-east-1

# Should return your token (if IAM role is configured correctly)
```

### Test Application Startup

```bash
# Start application
sudo systemctl start discord-bot

# Check logs
docker-compose logs -f discord-bot

# Look for successful parameter loading:
# "Loaded property source from AWS Parameter Store"
```

## Security Best Practices

### 1. Use Parameter Hierarchies

Organize parameters by environment:

```
/discord-bot/prod/discord-token
/discord-bot/prod/db-password
/discord-bot/dev/discord-token
/discord-bot/dev/db-password
```

### 2. Enable CloudTrail Logging

```bash
# Create CloudTrail to audit parameter access
aws cloudtrail create-trail \
  --name discord-bot-audit \
  --s3-bucket-name your-audit-bucket

aws cloudtrail start-logging --name discord-bot-audit
```

### 3. Rotate Credentials Regularly

```bash
# Update parameter value (creates new version)
aws ssm put-parameter \
  --name "/discord-bot/db-password" \
  --value "new_secure_password" \
  --type "SecureString" \
  --overwrite

# Restart application to pick up new value
docker-compose restart discord-bot
```

### 4. Use Parameter Store Tags

```bash
# Tag parameters for cost allocation and management
aws ssm add-tags-to-resource \
  --resource-type "Parameter" \
  --resource-id "/discord-bot/discord-token" \
  --tags "Key=Environment,Value=Production" "Key=CostCenter,Value=Engineering"
```

## Cost Analysis

### Parameter Store Costs

**Standard Parameters** (what you'll use):
- ✅ **FREE** for up to 10,000 parameters
- ✅ **FREE** API calls (standard throughput)

**Advanced Parameters** (if you need them):
- $0.05 per parameter per month
- Higher throughput limits
- Parameters > 4KB in size

### Comparison with Secrets Manager

| Feature | Parameter Store (Standard) | Secrets Manager |
|---------|---------------------------|-----------------|
| **Cost per secret** | FREE | $0.40/month |
| **API calls** | FREE (4k TPS) | $0.05/10k calls |
| **Rotation** | Manual | Automatic |
| **Best for** | Static config | DB credentials |

**For your use case**: Parameter Store is perfect and FREE!

## Troubleshooting

### Issue: "AccessDeniedException"

**Cause**: IAM role doesn't have permission

**Solution**:
```bash
# Verify IAM role is attached
aws ec2 describe-instances --instance-ids i-xxx --query 'Reservations[0].Instances[0].IamInstanceProfile'

# Test from EC2 instance
aws ssm get-parameter --name "/discord-bot/discord-token" --region us-east-1
```

### Issue: "ParameterNotFound"

**Cause**: Parameter doesn't exist or wrong name

**Solution**:
```bash
# List all parameters
aws ssm describe-parameters --region us-east-1

# Check parameter exists
aws ssm get-parameter --name "/discord-bot/discord-token" --region us-east-1
```

### Issue: "KMS.NotFoundException"

**Cause**: KMS key not found

**Solution**: Use default AWS managed key:
```bash
aws ssm put-parameter \
  --name "/discord-bot/discord-token" \
  --value "your_token" \
  --type "SecureString" \
  --region us-east-1
  # Don't specify --key-id, will use default aws/ssm key
```

## Monitoring

### CloudWatch Metrics

Monitor parameter access:
```bash
# View GetParameter API calls
aws cloudwatch get-metric-statistics \
  --namespace AWS/SSM \
  --metric-name GetParameterCount \
  --dimensions Name=ParameterName,Value=/discord-bot/discord-token \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-02T00:00:00Z \
  --period 3600 \
  --statistics Sum
```

### CloudTrail Audit

View who accessed parameters:
```bash
aws cloudtrail lookup-events \
  --lookup-attributes AttributeKey=EventName,AttributeValue=GetParameter \
  --max-results 10
```

## Migration from .env Files

### Step 1: Export existing .env to Parameter Store

```bash
#!/bin/bash
# migrate-to-parameter-store.sh

# Read .env file and create parameters
while IFS='=' read -r key value; do
  # Skip comments and empty lines
  [[ $key =~ ^#.*$ ]] && continue
  [[ -z $key ]] && continue

  # Create parameter
  aws ssm put-parameter \
    --name "/discord-bot/${key,,}" \
    --value "$value" \
    --type "SecureString" \
    --overwrite \
    --region us-east-1

  echo "Created parameter: /discord-bot/${key,,}"
done < .env
```

### Step 2: Remove .env file

```bash
# Backup first
cp .env .env.backup

# Remove sensitive file
rm .env
shred -u .env.backup  # Securely delete backup
```

## Next Steps

1. ✅ Store credentials in Parameter Store
2. ✅ Create and attach IAM role to EC2
3. ✅ Update application configuration
4. ✅ Test parameter retrieval
5. ✅ Deploy and verify
6. ✅ Remove old .env files
7. ✅ Enable CloudTrail for auditing

---

**Estimated Setup Time**: 20 minutes

**Security Improvement**: ⭐⭐⭐⭐⭐ (Excellent)

**Cost**: FREE (for standard parameters)
