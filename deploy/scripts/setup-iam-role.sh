#!/bin/bash
set -e

# Setup IAM Role for EC2 Instance with Parameter Store Access
# Run this script to create the IAM role and instance profile

echo "=== Discord Bot - IAM Role Setup ==="
echo ""

# Configuration
ROLE_NAME="DiscordBotEC2Role"
POLICY_NAME="DiscordBotParameterStoreAccess"
INSTANCE_PROFILE_NAME="DiscordBotEC2Profile"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "AWS Account ID: $AWS_ACCOUNT_ID"
echo "IAM Role Name: $ROLE_NAME"
echo "Policy Name: $POLICY_NAME"
echo "Instance Profile: $INSTANCE_PROFILE_NAME"
echo ""

# Check if running from deploy directory
if [ ! -f "../iam-policies/ec2-trust-policy.json" ]; then
  echo "Error: Please run this script from the deploy/scripts directory"
  exit 1
fi

# Step 1: Create IAM Role
echo "Step 1: Creating IAM role..."
aws iam create-role \
  --role-name "$ROLE_NAME" \
  --assume-role-policy-document file://../iam-policies/ec2-trust-policy.json \
  --description "IAM role for Discord Bot EC2 instance with Parameter Store access" \
  --tags "Key=Application,Value=discord-leetcode-bot" || echo "Role already exists"

# Step 2: Create IAM Policy
echo "Step 2: Creating IAM policy for Parameter Store access..."
POLICY_ARN="arn:aws:iam::${AWS_ACCOUNT_ID}:policy/${POLICY_NAME}"

aws iam create-policy \
  --policy-name "$POLICY_NAME" \
  --policy-document file://../iam-policies/parameter-store-access-policy.json \
  --description "Allows access to Discord Bot parameters in Parameter Store" \
  --tags "Key=Application,Value=discord-leetcode-bot" || echo "Policy already exists"

# Step 3: Attach Policy to Role
echo "Step 3: Attaching policy to role..."
aws iam attach-role-policy \
  --role-name "$ROLE_NAME" \
  --policy-arn "$POLICY_ARN"

# Step 4: Create Instance Profile
echo "Step 4: Creating EC2 instance profile..."
aws iam create-instance-profile \
  --instance-profile-name "$INSTANCE_PROFILE_NAME" \
  --tags "Key=Application,Value=discord-leetcode-bot" || echo "Instance profile already exists"

# Step 5: Add Role to Instance Profile
echo "Step 5: Adding role to instance profile..."
aws iam add-role-to-instance-profile \
  --instance-profile-name "$INSTANCE_PROFILE_NAME" \
  --role-name "$ROLE_NAME" || echo "Role already added to instance profile"

echo ""
echo "=== IAM Role Setup Complete ==="
echo ""
echo "IAM Role ARN: arn:aws:iam::${AWS_ACCOUNT_ID}:role/${ROLE_NAME}"
echo "Policy ARN: ${POLICY_ARN}"
echo "Instance Profile ARN: arn:aws:iam::${AWS_ACCOUNT_ID}:instance-profile/${INSTANCE_PROFILE_NAME}"
echo ""
echo "Next steps:"
echo "1. Attach this instance profile to your EC2 instance:"
echo "   aws ec2 associate-iam-instance-profile \\"
echo "     --instance-id i-XXXXXXXXX \\"
echo "     --iam-instance-profile Name=${INSTANCE_PROFILE_NAME}"
echo ""
echo "2. Or use it when launching a new instance:"
echo "   aws ec2 run-instances \\"
echo "     --image-id ami-XXXXXXXX \\"
echo "     --instance-type t3.medium \\"
echo "     --iam-instance-profile Name=${INSTANCE_PROFILE_NAME} \\"
echo "     ..."
