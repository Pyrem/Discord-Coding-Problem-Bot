#!/bin/bash
set -e

# Discord LeetCode Bot - Slash Command Registration
# This script registers the Discord slash commands for the bot
# Usage: ./scripts/register-commands.sh

if [ -z "$DISCORD_BOT_TOKEN" ]; then
    echo "Error: DISCORD_BOT_TOKEN environment variable is required"
    exit 1
fi

if [ -z "$DISCORD_APPLICATION_ID" ]; then
    echo "Error: DISCORD_APPLICATION_ID environment variable is required"
    exit 1
fi

echo "Registering Discord slash commands..."

# Register the /leetcode command globally
curl -X POST \
    "https://discord.com/api/v10/applications/$DISCORD_APPLICATION_ID/commands" \
    -H "Authorization: Bot $DISCORD_BOT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "name": "leetcode",
        "description": "Get LeetCode problems for a company",
        "options": [
            {
                "name": "query",
                "description": "Your request (e.g., \"Google problems from last 30 days\")",
                "type": 3,
                "required": true
            }
        ]
    }'

echo ""
echo "Slash commands registered successfully!"
echo ""
echo "Note: Global commands can take up to 1 hour to propagate."
echo "For faster testing, register commands to a specific guild:"
echo ""
echo "curl -X POST \\"
echo "    \"https://discord.com/api/v10/applications/\$DISCORD_APPLICATION_ID/guilds/YOUR_GUILD_ID/commands\" \\"
echo "    -H \"Authorization: Bot \$DISCORD_BOT_TOKEN\" \\"
echo "    -H \"Content-Type: application/json\" \\"
echo "    -d @commands.json"
