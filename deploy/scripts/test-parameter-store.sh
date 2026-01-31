#!/bin/bash
set -e

# Test Parameter Store Access
# Run this script on your EC2 instance to verify IAM role and parameters are configured correctly

echo "=== Testing Parameter Store Access ==="
echo ""

# Configuration
AWS_REGION="${AWS_REGION:-us-east-1}"
PREFIX="/discord-bot"

# Test 1: Check IAM role is attached
echo "Test 1: Checking if IAM role is attached to this EC2 instance..."
ROLE=$(curl -s http://169.254.169.254/latest/meta-data/iam/security-credentials/ || echo "")

if [ -z "$ROLE" ]; then
  echo "❌ FAILED: No IAM role attached to this instance"
  echo "   Please attach the DiscordBotEC2Profile instance profile"
  exit 1
else
  echo "✅ PASSED: IAM role attached: $ROLE"
fi
echo ""

# Test 2: List parameters
echo "Test 2: Listing parameters in $PREFIX..."
PARAMS=$(aws ssm get-parameters-by-path \
  --path "$PREFIX" \
  --region "$AWS_REGION" \
  --query "Parameters[].Name" \
  --output text 2>&1 || echo "ERROR")

if [[ "$PARAMS" == *"ERROR"* ]] || [ -z "$PARAMS" ]; then
  echo "❌ FAILED: Cannot list parameters"
  echo "   Error: $PARAMS"
  exit 1
else
  echo "✅ PASSED: Found parameters:"
  for param in $PARAMS; do
    echo "   - $param"
  done
fi
echo ""

# Test 3: Get Discord token (without decryption)
echo "Test 3: Getting Discord token parameter (encrypted)..."
DISCORD_TOKEN=$(aws ssm get-parameter \
  --name "$PREFIX/discord-token" \
  --region "$AWS_REGION" \
  --query "Parameter.Value" \
  --output text 2>&1 || echo "ERROR")

if [[ "$DISCORD_TOKEN" == *"ERROR"* ]]; then
  echo "❌ FAILED: Cannot retrieve discord-token"
  echo "   Error: $DISCORD_TOKEN"
  exit 1
else
  echo "✅ PASSED: Retrieved discord-token (encrypted value)"
fi
echo ""

# Test 4: Get Discord token with decryption
echo "Test 4: Getting Discord token with decryption..."
DISCORD_TOKEN_DECRYPTED=$(aws ssm get-parameter \
  --name "$PREFIX/discord-token" \
  --with-decryption \
  --region "$AWS_REGION" \
  --query "Parameter.Value" \
  --output text 2>&1 || echo "ERROR")

if [[ "$DISCORD_TOKEN_DECRYPTED" == *"ERROR"* ]]; then
  echo "❌ FAILED: Cannot decrypt discord-token"
  echo "   Error: $DISCORD_TOKEN_DECRYPTED"
  echo "   Check KMS permissions in IAM policy"
  exit 1
else
  echo "✅ PASSED: Successfully decrypted discord-token"
  echo "   Token length: ${#DISCORD_TOKEN_DECRYPTED} characters"
fi
echo ""

# Test 5: Get all required parameters
echo "Test 5: Verifying all required parameters exist..."
REQUIRED_PARAMS=("discord-token" "db-password" "db-username" "db-url")
ALL_EXIST=true

for param in "${REQUIRED_PARAMS[@]}"; do
  VALUE=$(aws ssm get-parameter \
    --name "$PREFIX/$param" \
    --region "$AWS_REGION" \
    --query "Parameter.Value" \
    --output text 2>&1 || echo "ERROR")

  if [[ "$VALUE" == *"ERROR"* ]]; then
    echo "❌ Missing parameter: $PREFIX/$param"
    ALL_EXIST=false
  else
    echo "✅ Found parameter: $PREFIX/$param"
  fi
done

if [ "$ALL_EXIST" = false ]; then
  echo ""
  echo "❌ FAILED: Some required parameters are missing"
  echo "   Run: ./setup-parameter-store.sh"
  exit 1
fi
echo ""

# Test 6: Simulate Spring Boot parameter loading
echo "Test 6: Simulating Spring Boot parameter loading..."
DB_USERNAME=$(aws ssm get-parameter --name "$PREFIX/db-username" --region "$AWS_REGION" --query "Parameter.Value" --output text)
DB_PASSWORD=$(aws ssm get-parameter --name "$PREFIX/db-password" --with-decryption --region "$AWS_REGION" --query "Parameter.Value" --output text)
DB_URL=$(aws ssm get-parameter --name "$PREFIX/db-url" --region "$AWS_REGION" --query "Parameter.Value" --output text)

echo "✅ PASSED: Successfully loaded all parameters"
echo "   DB_USERNAME: $DB_USERNAME"
echo "   DB_PASSWORD: ${DB_PASSWORD:0:4}****** (hidden)"
echo "   DB_URL: $DB_URL"
echo ""

echo "=== All Tests Passed! ==="
echo ""
echo "Your EC2 instance is correctly configured to use Parameter Store"
echo "You can now start the Discord bot with profile: ec2-paramstore"
echo ""
echo "To start the application:"
echo "  export SPRING_PROFILES_ACTIVE=ec2-paramstore"
echo "  export AWS_REGION=$AWS_REGION"
echo "  docker-compose up -d"
