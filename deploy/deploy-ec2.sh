#!/bin/bash
set -e

# EC2 Deployment Script for Discord LeetCode Bot
# Usage: ./deploy-ec2.sh

echo "=== Discord LeetCode Bot - EC2 Deployment ==="

# Configuration
AWS_REGION="${AWS_REGION:-us-east-1}"
ECR_REPOSITORY="discord-leetcode-bot"
IMAGE_TAG="${IMAGE_TAG:-latest}"

# Get AWS Account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "AWS Account ID: $AWS_ACCOUNT_ID"
echo "AWS Region: $AWS_REGION"
echo "ECR Repository: $ECR_REPOSITORY"
echo "Image Tag: $IMAGE_TAG"

# Step 1: Build Docker image locally
echo ""
echo "Step 1: Building Docker image..."
docker build -t $ECR_REPOSITORY:$IMAGE_TAG ..

# Step 2: Create ECR repository if it doesn't exist
echo ""
echo "Step 2: Creating ECR repository (if needed)..."
aws ecr describe-repositories --repository-names $ECR_REPOSITORY --region $AWS_REGION 2>/dev/null || \
    aws ecr create-repository --repository-name $ECR_REPOSITORY --region $AWS_REGION

# Step 3: Authenticate Docker to ECR
echo ""
echo "Step 3: Authenticating to ECR..."
aws ecr get-login-password --region $AWS_REGION | \
    docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Step 4: Tag image
echo ""
echo "Step 4: Tagging image..."
docker tag $ECR_REPOSITORY:$IMAGE_TAG \
    $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG

# Step 5: Push to ECR
echo ""
echo "Step 5: Pushing image to ECR..."
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG

echo ""
echo "=== Deployment Complete ==="
echo "Image pushed to: $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"
echo ""
echo "Next steps:"
echo "1. SSH into your EC2 instance"
echo "2. Pull the image from ECR"
echo "3. Run docker-compose up with the ec2-docker-compose.yml file"
