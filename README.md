# Discord LeetCode Bot

A Spring Boot Discord bot that monitors Discord channels, processes natural language requests for LeetCode company problem sets, and returns curated problem lists with intelligent caching.

## Features

- **Natural Language Processing**: Uses Spring AI with Ollama (llama3.2) to parse Discord messages
- **Intelligent Caching**: Write-through cache with 30-day expiration in PostgreSQL
- **Automatic Time Range Selection**: Finds the most recent problem set with at least 30 problems
- **Rich Discord Embeds**: Beautiful problem displays similar to LeetCode's interface
- **Dynamic Table Management**: Creates company-specific tables for denormalized data storage
- **Multi-Profile Support**: Separate configurations for local development and EC2 deployment

## Architecture

- **Frontend**: Discord chat interface (via JDA)
- **Backend**: Spring Boot 3.5.7 with Spring AI
- **Database**: PostgreSQL with dynamic table creation
- **NLP Model**: Ollama llama3.2 (3B) for request parsing
- **Build System**: Maven

## Prerequisites

- Java 21
- PostgreSQL 12+
- Ollama with llama3.2 model installed
- Discord Bot Token

## Quick Start

### 1. Install Ollama and Pull Model

```bash
# Install Ollama (visit https://ollama.ai for installation instructions)
# Pull the llama3.2 model
ollama pull llama3.2
```

### 2. Set Up PostgreSQL

```bash
# Create database
createdb leetcode_bot

# Or using psql
psql -U postgres -c "CREATE DATABASE leetcode_bot;"
```

### 3. Configure Discord Bot Token

Edit `src/main/resources/application.properties` and set your Discord bot token:

```properties
discord.bot.token=YOUR_DISCORD_BOT_TOKEN_HERE
```

### 4. Build and Run

```bash
# Build the project
mvn clean package

# Run locally
mvn spring-boot:run

# Or run the JAR
java -jar target/discord-leetcode-bot-1.0.0-SNAPSHOT.jar
```

## Configuration

### Application Profiles

**Local Development** (`application-local.properties`):
- PostgreSQL on localhost:5432
- Ollama on localhost:11434
- Verbose logging

**EC2 Production** (`application-ec2.properties`):
- Environment variable support for credentials
- Connection pooling optimized for production
- Reduced logging

Activate a profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ec2
```

### Key Configuration Properties

```properties
# Cache expiry (days)
leetcode.cache.expiry.days=30

# Minimum problems required in a set
leetcode.problemset.min.size=30

# Maximum problems to store/return
leetcode.problemset.max.size=50

# Ollama configuration
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
```

## Usage Examples

### Discord Commands

Simply type natural language requests in any Discord channel where the bot has access:

```
Microsoft?
Google problems from last 30 days
Amazon and Meta 6 months
Show me Apple LeetCode questions
```

### Response Format

The bot returns rich embeds showing:
- Problem number and name (clickable link to LeetCode)
- Acceptance rate
- Difficulty level (color-coded)
- Frequency visualization (progress bar)

## Project Structure

```
src/main/java/com/pyrem/leetcodebot/
├── DiscordLeetCodeBotApplication.java  # Main application class
├── config/                              # Spring configuration
│   └── SpringAiConfig.java
├── discord/                             # Discord bot integration
│   └── DiscordBotService.java
├── model/                               # Domain models
│   ├── CachedProblemSet.java
│   ├── CompanyProblemRequest.java
│   ├── LeetCodeProblem.java
│   ├── ProblemDifficulty.java
│   └── TimeRange.java
├── nlp/                                 # Natural language processing
│   └── RequestParserService.java
├── repository/                          # Data access layer
│   ├── CachedProblemSetRepository.java
│   └── DynamicProblemSetRepository.java
└── service/                             # Business logic
    ├── LeetCodeService.java
    └── MockLeetCodeClient.java
```

## How It Works

1. **Message Reception**: Discord bot receives message via JDA
2. **NLP Parsing**: Spring AI + Ollama parses message into structured request
3. **Cache Check**: Queries PostgreSQL for cached problem sets (checks expiration)
4. **Time Range Selection**: If not explicit, finds most recent range with ≥30 problems
5. **Data Fetching**: If cache miss/expired, fetches from LeetCode API (currently mocked)
6. **Storage**: Saves problems in company-specific table (e.g., `microsoft_last30days`)
7. **Response**: Sends rich Discord embeds with problem details

## Database Schema

### Metadata Table: `cached_problem_sets`
- Tracks all cached problem sets
- Stores: company, time range, table name, problem count, last updated

### Dynamic Problem Tables (e.g., `microsoft_last30days`)
- One table per company-timerange combination
- Stores: problem number, name, acceptance rate, difficulty, frequency, URL

## Docker Deployment

### Local Development with Docker

```bash
# Create .env file from template
cp .env.example .env
# Edit .env and add your Discord bot token

# Start all services
docker-compose up -d

# Pull Ollama model (one-time setup)
docker exec -it leetcode-bot-ollama ollama pull llama3.2

# View logs
docker-compose logs -f discord-bot

# Stop services
docker-compose down
```

### AWS Deployment

Multiple deployment options available for AWS:

1. **Single EC2 Instance** (Recommended for getting started)
   - Simple setup with Docker Compose
   - ~$35/month
   - See: `deploy/AWS_DEPLOYMENT_GUIDE.md`

2. **AWS ECS Fargate** (Recommended for production)
   - Fully managed, auto-scaling
   - ~$64/month
   - See: `deploy/AWS_DEPLOYMENT_GUIDE.md`

3. **AWS ECS on EC2** (Cost-optimized)
   - Balance of control and automation
   - ~$48/month
   - See: `deploy/AWS_DEPLOYMENT_GUIDE.md`

**For detailed AWS deployment instructions, see [AWS Deployment Guide](deploy/AWS_DEPLOYMENT_GUIDE.md)**

## TODO

- [ ] Implement actual LeetCode API client (replace `MockLeetCodeClient`)
- [ ] Add support for filtering by difficulty
- [ ] Implement pagination for large result sets
- [ ] Add admin commands for cache management
- [ ] Add unit and integration tests
- [x] Set up Docker containerization
- [ ] Add metrics and monitoring
- [ ] Add health check endpoints

## Contributing

This is a personal project. Feel free to fork and customize for your needs.

## License

MIT License
