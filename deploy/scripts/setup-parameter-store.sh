#!/bin/bash
set -e

# Setup AWS Systems Manager Parameter Store for Discord Bot
# Run this script to create all required parameters

echo "=== Discord Bot - Parameter Store Setup ==="
echo ""

# Configuration
AWS_REGION="${AWS_REGION:-us-east-1}"
PREFIX="/discord-bot"

# Prompt for values
read -p "Enter Discord Bot Token: " DISCORD_TOKEN
read -s -p "Enter PostgreSQL Password: " DB_PASSWORD
echo ""
read -p "Enter PostgreSQL Username [postgres]: " DB_USERNAME
DB_USERNAME="${DB_USERNAME:-postgres}"
read -p "Enter PostgreSQL URL [jdbc:postgresql://localhost:5432/leetcode_bot]: " DB_URL
DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/leetcode_bot}"

echo ""
echo "Creating parameters in AWS Parameter Store..."
echo "Region: $AWS_REGION"
echo "Prefix: $PREFIX"
echo ""

# Create Discord Bot Token (SecureString)
echo "Creating parameter: $PREFIX/discord-token"
aws ssm put-parameter \
  --name "$PREFIX/discord-token" \
  --value "$DISCORD_TOKEN" \
  --type "SecureString" \
  --description "Discord bot authentication token" \
  --tags "Key=Application,Value=discord-leetcode-bot" "Key=Environment,Value=production" \
  --region "$AWS_REGION" \
  --overwrite || echo "Parameter already exists, use --overwrite to update"

# Create Database Password (SecureString)
echo "Creating parameter: $PREFIX/db-password"
aws ssm put-parameter \
  --name "$PREFIX/db-password" \
  --value "$DB_PASSWORD" \
  --type "SecureString" \
  --description "PostgreSQL database password" \
  --tags "Key=Application,Value=discord-leetcode-bot" "Key=Environment,Value=production" \
  --region "$AWS_REGION" \
  --overwrite || echo "Parameter already exists, use --overwrite to update"

# Create Database Username (String)
echo "Creating parameter: $PREFIX/db-username"
aws ssm put-parameter \
  --name "$PREFIX/db-username" \
  --value "$DB_USERNAME" \
  --type "String" \
  --description "PostgreSQL database username" \
  --tags "Key=Application,Value=discord-leetcode-bot" "Key=Environment,Value=production" \
  --region "$AWS_REGION" \
  --overwrite || echo "Parameter already exists, use --overwrite to update"

# Create Database URL (String)
echo "Creating parameter: $PREFIX/db-url"
aws ssm put-parameter \
  --name "$PREFIX/db-url" \
  --value "$DB_URL" \
  --type "String" \
  --description "PostgreSQL database connection URL" \
  --tags "Key=Application,Value=discord-leetcode-bot" "Key=Environment,Value=production" \
  --region "$AWS_REGION" \
  --overwrite || echo "Parameter already exists, use --overwrite to update"

# Optional: Ollama URL (default is localhost)
echo "Creating parameter: $PREFIX/ollama-url"
aws ssm put-parameter \
  --name "$PREFIX/ollama-url" \
  --value "http://localhost:11434" \
  --type "String" \
  --description "Ollama API endpoint URL" \
  --tags "Key=Application,Value=discord-leetcode-bot" "Key=Environment,Value=production" \
  --region "$AWS_REGION" \
  --overwrite || echo "Parameter already exists, use --overwrite to update"

echo ""
echo "=== Parameter Store Setup Complete ==="
echo ""
echo "Created parameters:"
echo "  $PREFIX/discord-token (SecureString)"
echo "  $PREFIX/db-password (SecureString)"
echo "  $PREFIX/db-username (String)"
echo "  $PREFIX/db-url (String)"
echo "  $PREFIX/ollama-url (String)"
echo ""
echo "To verify, run:"
echo "  aws ssm get-parameters-by-path --path $PREFIX --region $AWS_REGION"
echo ""
echo "To view a specific parameter (with decryption):"
echo "  aws ssm get-parameter --name $PREFIX/discord-token --with-decryption --region $AWS_REGION"
