#!/bin/bash
set -e

# Discord LeetCode Bot - Local Testing Script
# Usage: ./scripts/local-test.sh

echo "=========================================="
echo "Discord LeetCode Bot - Local Testing"
echo "=========================================="

# Check if SAM CLI is installed
if ! command -v sam &> /dev/null; then
    echo "Error: AWS SAM CLI is not installed"
    echo "Install it from: https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html"
    exit 1
fi

# Build the project
echo ""
echo "Building project..."
mvn clean package -DskipTests

# Build SAM application
echo ""
echo "Building SAM application..."
sam build

# Create test event
echo ""
echo "Creating test event..."
cat > /tmp/test-event.json << 'EOF'
{
    "body": "{\"type\":1}",
    "headers": {
        "x-signature-ed25519": "test-signature",
        "x-signature-timestamp": "1234567890"
    },
    "httpMethod": "POST",
    "path": "/discord/interactions"
}
EOF

# Invoke locally (note: signature verification will fail without proper keys)
echo ""
echo "Invoking Lambda locally..."
echo "Note: Signature verification will fail without proper Discord public key"
sam local invoke DiscordInteractionFunction \
    --event /tmp/test-event.json \
    --env-vars <(echo '{
        "DiscordInteractionFunction": {
            "DISCORD_PUBLIC_KEY": "test-key",
            "DISCORD_BOT_TOKEN": "test-token",
            "DISCORD_APPLICATION_ID": "test-app-id",
            "DYNAMODB_TABLE_NAME": "leetcode-problems-dev",
            "BEDROCK_MODEL_ID": "anthropic.claude-3-haiku-20240307-v1:0"
        }
    }')

echo ""
echo "=========================================="
echo "Local testing complete!"
echo "=========================================="
