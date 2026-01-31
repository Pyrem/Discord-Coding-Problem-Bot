#!/bin/bash
set -e

# Discord LeetCode Bot - Deployment Script
# Usage: ./scripts/deploy.sh [dev|prod]

ENVIRONMENT=${1:-dev}

echo "=========================================="
echo "Discord LeetCode Bot - Deployment"
echo "Environment: $ENVIRONMENT"
echo "=========================================="

# Check required environment variables
if [ -z "$DISCORD_PUBLIC_KEY" ]; then
    echo "Error: DISCORD_PUBLIC_KEY environment variable is required"
    exit 1
fi

if [ -z "$DISCORD_BOT_TOKEN" ]; then
    echo "Error: DISCORD_BOT_TOKEN environment variable is required"
    exit 1
fi

if [ -z "$DISCORD_APPLICATION_ID" ]; then
    echo "Error: DISCORD_APPLICATION_ID environment variable is required"
    exit 1
fi

# Build the project
echo ""
echo "Building project..."
mvn clean package -DskipTests

# Validate SAM template
echo ""
echo "Validating SAM template..."
sam validate

# Build SAM application
echo ""
echo "Building SAM application..."
sam build

# Deploy
echo ""
echo "Deploying to AWS..."
sam deploy \
    --config-env $ENVIRONMENT \
    --parameter-overrides \
        "DiscordPublicKey=$DISCORD_PUBLIC_KEY" \
        "DiscordBotToken=$DISCORD_BOT_TOKEN" \
        "DiscordApplicationId=$DISCORD_APPLICATION_ID" \
        "Environment=$ENVIRONMENT"

echo ""
echo "=========================================="
echo "Deployment complete!"
echo "=========================================="

# Get outputs
echo ""
echo "Stack Outputs:"
aws cloudformation describe-stacks \
    --stack-name "discord-leetcode-bot-$ENVIRONMENT" \
    --query 'Stacks[0].Outputs' \
    --output table
