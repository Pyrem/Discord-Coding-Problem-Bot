# AWS Deployment Guide for Discord LeetCode Bot

This guide covers deploying the Discord LeetCode Bot on AWS using Docker with three different approaches.

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Deployment Options](#deployment-options)
3. [Option 1: Single EC2 Instance (Recommended for Start)](#option-1-single-ec2-instance)
4. [Option 2: AWS ECS Fargate](#option-2-aws-ecs-fargate)
5. [Option 3: AWS ECS on EC2](#option-3-aws-ecs-on-ec2)
6. [Cost Comparison](#cost-comparison)

---

## Architecture Overview

### Components to Deploy
```
┌─────────────────────────────────────────────────────────┐
│                     AWS Cloud                           │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │ Discord Bot Container                          │    │
│  │ - Spring Boot App                              │    │
│  │ - JDA (Discord client)                         │    │
│  │ - Spring AI integration                        │    │
│  └──────────┬──────────────┬──────────────────────┘    │
│             │              │                            │
│             ↓              ↓                            │
│  ┌──────────────┐   ┌──────────────┐                  │
│  │ PostgreSQL   │   │ Ollama       │                  │
│  │ Container    │   │ Container    │                  │
│  │              │   │ (llama3.2)   │                  │
│  └──────────────┘   └──────────────┘                  │
│                                                         │
└─────────────────────────────────────────────────────────┘
                           │
                           ↓
                  Discord API (External)
```

### Storage Requirements
- **PostgreSQL Data**: ~1-5 GB (grows with cached problem sets)
- **Ollama Models**: ~2 GB (llama3.2 model)
- **Application**: ~500 MB

### Compute Requirements
- **CPU**: 2 vCPUs minimum (Ollama benefits from more)
- **RAM**: 4 GB minimum (2 GB for Ollama, 1 GB for Spring Boot, 1 GB for PostgreSQL)
- **Disk**: 20 GB minimum

---

## Deployment Options

### Comparison Table

| Feature | Single EC2 | ECS Fargate | ECS on EC2 |
|---------|-----------|-------------|------------|
| **Complexity** | Low | Medium | High |
| **Cost** | $30-50/mo | $50-80/mo | $40-70/mo |
| **Maintenance** | Manual | Low | Medium |
| **Scaling** | Manual | Auto | Auto |
| **Best For** | Development, Small scale | Production, Auto-scaling | Cost optimization |

---

## Option 1: Single EC2 Instance

**Best for**: Getting started, development, low-cost production

### Architecture
- One EC2 instance running Docker Compose
- All three containers on the same instance
- Persistent volumes on EBS

### Step-by-Step Deployment

#### 1. Launch EC2 Instance

**Recommended Instance Type**: `t3.medium` or `t3a.medium`
- 2 vCPUs, 4 GB RAM
- Cost: ~$30/month

```bash
# Using AWS CLI
aws ec2 run-instances \
  --image-id ami-0c55b159cbfafe1f0 \
  --instance-type t3.medium \
  --key-name your-key-pair \
  --security-group-ids sg-your-security-group \
  --subnet-id subnet-your-subnet \
  --block-device-mappings '[{"DeviceName":"/dev/xvda","Ebs":{"VolumeSize":30,"VolumeType":"gp3"}}]' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=discord-leetcode-bot}]'
```

**Security Group Requirements**:
- **Outbound**: Allow all (for Discord API, Docker Hub, etc.)
- **Inbound**:
  - SSH (22) from your IP only
  - No other ports needed (bot doesn't expose public endpoints)

#### 2. Install Docker on EC2

SSH into your instance and install Docker:

```bash
# SSH into instance
ssh -i your-key.pem ec2-user@your-instance-ip

# Update system
sudo yum update -y

# Install Docker
sudo yum install -y docker

# Start Docker service
sudo systemctl start docker
sudo systemctl enable docker

# Add ec2-user to docker group
sudo usermod -a -G docker ec2-user

# Install Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Log out and back in for group changes to take effect
exit
```

#### 3. Set Up Application Directory

```bash
# SSH back in
ssh -i your-key.pem ec2-user@your-instance-ip

# Create application directory
mkdir -p ~/discord-bot
cd ~/discord-bot

# Create data directories with proper permissions
sudo mkdir -p /data/postgres /data/ollama
sudo chown -R 999:999 /data/postgres  # PostgreSQL user ID
sudo chown -R ec2-user:ec2-user /data/ollama
```

#### 4. Configure Environment Variables

```bash
# Create .env file
cat > .env << 'EOF'
# Discord Configuration
DISCORD_BOT_TOKEN=your_discord_token_here

# Database Configuration
DB_PASSWORD=your_secure_password_here

# AWS Configuration (for ECR)
AWS_ACCOUNT_ID=123456789012
AWS_REGION=us-east-1
EOF

# Secure the .env file
chmod 600 .env
```

#### 5. Build and Push Docker Image

**On your local machine** (with AWS CLI configured):

```bash
# Navigate to project root
cd /path/to/Discord-Coding-Problem-Bot

# Run deployment script
./deploy/deploy-ec2.sh
```

This script will:
1. Build the Docker image
2. Create ECR repository
3. Push image to Amazon ECR

#### 6. Deploy with Docker Compose

**On EC2 instance**:

```bash
cd ~/discord-bot

# Download docker-compose file
cat > docker-compose.yml << 'EOF'
# Paste contents of deploy/ec2-docker-compose.yml here
EOF

# Load environment variables
export $(cat .env | xargs)

# Authenticate to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Pull images
docker-compose pull

# Start services
docker-compose up -d

# Check logs
docker-compose logs -f
```

#### 7. Pull Ollama Model

```bash
# Wait for Ollama to start (check logs)
docker-compose logs -f ollama

# Once running, pull the model
docker exec -it discord-bot-ollama ollama pull llama3.2

# Verify model is loaded
docker exec -it discord-bot-ollama ollama list
```

#### 8. Verify Deployment

```bash
# Check all containers are running
docker-compose ps

# Check application logs
docker-compose logs discord-bot

# Check for successful Discord connection
docker-compose logs discord-bot | grep "Discord bot initialized"
```

#### 9. Set Up Auto-Start on Reboot

```bash
# Create systemd service
sudo tee /etc/systemd/system/discord-bot.service > /dev/null << 'EOF'
[Unit]
Description=Discord LeetCode Bot
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ec2-user/discord-bot
ExecStart=/usr/local/bin/docker-compose up -d
ExecStop=/usr/local/bin/docker-compose down
User=ec2-user

[Install]
WantedBy=multi-user.target
EOF

# Enable service
sudo systemctl enable discord-bot.service
```

---

## Option 2: AWS ECS Fargate

**Best for**: Production with auto-scaling, minimal infrastructure management

### Architecture
```
┌─────────────────────────────────────────────────────────┐
│                     AWS VPC                             │
│  ┌──────────────────────────────────────────────────┐   │
│  │ ECS Fargate Cluster                              │   │
│  │  ┌─────────────────────────────────────────┐     │   │
│  │  │ Task Definition                         │     │   │
│  │  │  ┌──────────────┐  ┌──────────────┐     │     │   │
│  │  │  │ Discord Bot  │  │ Ollama       │     │     │   │
│  │  │  │ Container    │  │ Container    │     │     │   │
│  │  │  └──────────────┘  └──────────────┘     │     │   │
│  │  └─────────────────────────────────────────┘     │   │
│  └──────────────────────────────────────────────────┘   │
│                          │                               │
│                          ↓                               │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Amazon RDS PostgreSQL                            │   │
│  │ - Multi-AZ for HA                                │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Amazon EFS (for Ollama models)                   │   │
│  │ - Shared storage across tasks                    │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Prerequisites

1. **RDS PostgreSQL Instance**
2. **EFS File System** (for Ollama models)
3. **ECR Repository** (for Docker image)
4. **Secrets Manager** (for credentials)
5. **IAM Roles** (for ECS tasks)

### Step-by-Step Deployment

#### 1. Create RDS PostgreSQL Instance

```bash
aws rds create-db-instance \
  --db-instance-identifier leetcode-bot-db \
  --db-instance-class db.t4g.micro \
  --engine postgres \
  --engine-version 16.1 \
  --master-username postgres \
  --master-user-password YourSecurePassword123! \
  --allocated-storage 20 \
  --vpc-security-group-ids sg-your-security-group \
  --db-subnet-group-name your-db-subnet-group \
  --backup-retention-period 7 \
  --storage-encrypted \
  --publicly-accessible false
```

#### 2. Create EFS File System

```bash
# Create EFS file system
aws efs create-file-system \
  --performance-mode generalPurpose \
  --throughput-mode bursting \
  --encrypted \
  --tags Key=Name,Value=leetcode-bot-ollama

# Create mount targets in each subnet
aws efs create-mount-target \
  --file-system-id fs-your-efs-id \
  --subnet-id subnet-your-subnet-1 \
  --security-groups sg-your-efs-security-group

# Create access point
aws efs create-access-point \
  --file-system-id fs-your-efs-id \
  --posix-user Uid=1000,Gid=1000 \
  --root-directory "Path=/ollama,CreationInfo={OwnerUid=1000,OwnerGid=1000,Permissions=755}"
```

#### 3. Store Secrets in AWS Secrets Manager

```bash
# Store Discord bot token
aws secretsmanager create-secret \
  --name discord-bot-token \
  --secret-string "your_discord_bot_token_here"

# Store database password
aws secretsmanager create-secret \
  --name postgres-password \
  --secret-string "YourSecurePassword123!"
```

#### 4. Create ECR Repository and Push Image

```bash
# Create repository
aws ecr create-repository --repository-name discord-leetcode-bot

# Build and push (from project root)
./deploy/deploy-ec2.sh
```

#### 5. Create ECS Task Definition

Edit `deploy/aws-ecs-task-definition.json` with your values, then:

```bash
aws ecs register-task-definition \
  --cli-input-json file://deploy/aws-ecs-task-definition.json
```

#### 6. Create ECS Cluster

```bash
aws ecs create-cluster --cluster-name discord-leetcode-bot-cluster
```

#### 7. Create ECS Service

```bash
aws ecs create-service \
  --cluster discord-leetcode-bot-cluster \
  --service-name discord-bot-service \
  --task-definition discord-leetcode-bot:1 \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-your-subnet],securityGroups=[sg-your-sg],assignPublicIp=ENABLED}"
```

#### 8. Pull Ollama Model

You'll need to run this once after the task starts:

```bash
# Get task ID
TASK_ID=$(aws ecs list-tasks --cluster discord-leetcode-bot-cluster --service-name discord-bot-service --query 'taskArns[0]' --output text)

# Execute command in Ollama container
aws ecs execute-command \
  --cluster discord-leetcode-bot-cluster \
  --task $TASK_ID \
  --container ollama \
  --interactive \
  --command "ollama pull llama3.2"
```

---

## Option 3: AWS ECS on EC2

**Best for**: Cost optimization with auto-scaling needs

Similar to Fargate but you manage the EC2 instances in the ECS cluster. This gives you more control and potentially lower costs but requires more management.

---

## Cost Comparison

### Option 1: Single EC2 (t3.medium)
```
EC2 Instance:     $30/month (t3.medium)
EBS Storage:      $3/month (30 GB gp3)
Data Transfer:    $2/month (minimal)
Total:            ~$35/month
```

### Option 2: ECS Fargate
```
Fargate (1 task):  $45/month (1 vCPU, 2 GB RAM)
RDS (db.t4g.micro): $15/month
EFS Storage:       $3/month (10 GB)
Secrets Manager:   $1/month
Total:             ~$64/month
```

### Option 3: ECS on EC2
```
EC2 Instance:      $30/month (t3.medium)
RDS (db.t4g.micro): $15/month
EBS Storage:       $3/month
Total:             ~$48/month
```

---

## Maintenance & Operations

### Updating the Application

**EC2 Deployment**:
```bash
# On local machine: rebuild and push
./deploy/deploy-ec2.sh

# On EC2: pull and restart
docker-compose pull discord-bot
docker-compose up -d discord-bot
```

**ECS Fargate**:
```bash
# Push new image
./deploy/deploy-ec2.sh

# Update service to force new deployment
aws ecs update-service \
  --cluster discord-leetcode-bot-cluster \
  --service discord-bot-service \
  --force-new-deployment
```

### Monitoring

Add CloudWatch logging to monitor your application:

```bash
# View logs (EC2)
docker-compose logs -f discord-bot

# View logs (ECS Fargate)
aws logs tail /ecs/discord-leetcode-bot --follow
```

### Backup Strategy

**PostgreSQL Data**:
- EC2: Use EBS snapshots
- RDS: Automated backups enabled (7-day retention)

**Ollama Models**:
- Can be re-downloaded if lost
- Consider EFS backups for faster recovery

---

## Recommended Approach

**For beginners**: Start with **Option 1 (Single EC2)**
- Simplest setup
- Easy to debug
- Lowest cost
- Can migrate to ECS later

**For production**: Use **Option 2 (ECS Fargate)**
- Better reliability
- Auto-scaling capabilities
- Managed infrastructure
- Better for compliance

---

## Next Steps

1. Choose your deployment option
2. Follow the step-by-step guide
3. Test the bot in Discord
4. Set up monitoring and alerts
5. Plan for scaling if needed

## Troubleshooting

See the main README.md for troubleshooting tips and common issues.
