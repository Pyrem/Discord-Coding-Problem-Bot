# LLM Hosting Cost Analysis: Self-Hosted EC2 vs Cloud API

## Executive Summary

This analysis compares self-hosting a Llama model on EC2 versus using a cloud-hosted LLM API (AWS Bedrock) for the Discord LeetCode Bot's natural language processing needs.

**Usage Assumption**: 10 user messages per day = 300 messages/month = 3,650 messages/year

| Approach | Monthly Cost | Annual Cost | Latency | Maintenance |
|----------|-------------|-------------|---------|-------------|
| EC2 + Ollama (CPU) | $30-35 | $360-420 | 5-10 sec | High |
| EC2 + Ollama (GPU) | $384+ | $4,608+ | 0.3-0.5 sec | High |
| AWS Bedrock (Claude Haiku) | $0.15-0.50 | $2-6 | 1-2 sec | Zero |
| AWS Bedrock (Claude Sonnet) | $0.90-3.00 | $11-36 | 1-2 sec | Zero |

**Recommendation**: For 10 messages/day, **AWS Bedrock with Claude 3 Haiku** is the clear winner—**99% cost savings** vs self-hosted EC2.

---

## Detailed Cost Breakdown

### Option 1: Self-Hosted EC2 with Ollama (CPU-Only)

**Instance**: t3.medium (2 vCPU, 4 GB RAM)

| Component | Monthly Cost | Notes |
|-----------|-------------|-------|
| EC2 t3.medium (on-demand) | $30.37 | 24/7 operation |
| EBS Storage (30 GB gp3) | $2.40 | OS + model storage |
| Data Transfer (1 GB/month) | $0.09 | Discord API traffic |
| **Total** | **$32.86** | |

**With Reserved Instance (1-year)**: ~$20/month (37% savings)

**Performance Characteristics**:
- Model: llama3.2 (3B parameters)
- Inference latency: **5-10 seconds per request** (CPU bottleneck)
- Concurrent capacity: 1-2 requests (queuing required)
- Cold start: N/A (always running)

**Hidden Costs**:
- Time spent on maintenance, updates, security patches
- Monitoring and alerting setup
- Downtime risk (no redundancy)

---

### Option 2: Self-Hosted EC2 with Ollama (GPU-Accelerated)

**Instance**: g4dn.xlarge (4 vCPU, 16 GB RAM, NVIDIA T4 GPU)

| Component | Monthly Cost | Notes |
|-----------|-------------|-------|
| EC2 g4dn.xlarge (on-demand) | $379.08 | 24/7 operation |
| EBS Storage (50 GB gp3) | $4.00 | OS + CUDA + model |
| Data Transfer | $0.09 | Discord API traffic |
| **Total** | **$383.17** | |

**With Spot Instance**: ~$115/month (70% savings, but can be interrupted)

**Performance Characteristics**:
- Model: llama3.2 (3B) or larger models
- Inference latency: **0.3-0.5 seconds per request**
- Concurrent capacity: 5-10 requests
- GPU utilization for 10 req/day: **< 0.01%** (massively underutilized)

---

### Option 3: AWS Bedrock (Managed LLM API)

**Pricing Model**: Pay-per-token (no idle costs)

#### Claude 3 Haiku (Recommended for simple NLP tasks)

| Metric | Value |
|--------|-------|
| Input tokens | $0.00025 per 1K tokens |
| Output tokens | $0.00125 per 1K tokens |

**Per-Request Cost Estimate**:
- Average input: ~150 tokens (user message + system prompt)
- Average output: ~50 tokens (JSON response)
- Cost per request: (150/1000 × $0.00025) + (50/1000 × $0.00125) = **$0.0001**

| Traffic Level | Requests/Month | Monthly Cost |
|---------------|----------------|--------------|
| Your usage (10/day) | 300 | **$0.03** |
| Light (50/day) | 1,500 | $0.15 |
| Medium (100/day) | 3,000 | $0.30 |
| Heavy (1000/day) | 30,000 | $3.00 |

#### Claude 3.5 Sonnet (Higher quality, still affordable)

| Metric | Value |
|--------|-------|
| Input tokens | $0.003 per 1K tokens |
| Output tokens | $0.015 per 1K tokens |

**Per-Request Cost**: (150/1000 × $0.003) + (50/1000 × $0.015) = **$0.0012**

| Traffic Level | Requests/Month | Monthly Cost |
|---------------|----------------|--------------|
| Your usage (10/day) | 300 | **$0.36** |
| Light (50/day) | 1,500 | $1.80 |
| Medium (100/day) | 3,000 | $3.60 |

---

## Tradeoff Analysis

### Cost Efficiency

```
Monthly Cost Comparison (10 messages/day = 300/month)

EC2 CPU (t3.medium):     $32.86  ████████████████████████████████████████
EC2 GPU (g4dn.xlarge):  $383.17  ████████████████████████████████████████████████████...
Bedrock Haiku:            $0.03  ▌ (barely visible)
Bedrock Sonnet:           $0.36  ▌

Cost per request:
EC2 CPU:     $0.11 per request ($32.86 / 300)
EC2 GPU:     $1.28 per request ($383.17 / 300)
Bedrock:   $0.0001 per request

Breakeven point: EC2 becomes cheaper at ~330,000 requests/month
                 (11,000 requests/day)
```

### Performance

| Metric | EC2 CPU | EC2 GPU | Bedrock |
|--------|---------|---------|---------|
| Latency | 5-10 sec | 0.3-0.5 sec | 1-2 sec |
| Cold start | None | None | 50-200ms |
| Throughput | 6-12 req/min | 120+ req/min | 1000+ req/min |
| Availability | 99.5% (single instance) | 99.5% | 99.9% (SLA) |

### Operational Complexity

| Factor | EC2 (Self-Hosted) | Bedrock (Managed API) |
|--------|-------------------|----------------------|
| Setup time | 2-4 hours | 15 minutes |
| Maintenance | Ongoing (OS, security, model updates) | None |
| Scaling | Manual (resize/add instances) | Automatic |
| Monitoring | DIY (CloudWatch, custom) | Built-in |
| Security patches | Your responsibility | AWS managed |
| Model updates | Manual download | Automatic |
| Disaster recovery | DIY | Built-in |

### Quality & Accuracy

| Model | Quality for NLP Parsing | Notes |
|-------|------------------------|-------|
| llama3.2 (3B) | Good | Occasional JSON formatting issues |
| llama3.2 (1B quantized) | Moderate | Faster but less accurate |
| Claude 3 Haiku | Excellent | Better instruction following |
| Claude 3.5 Sonnet | Superior | Best accuracy, overkill for simple parsing |

---

## Decision Framework

### Choose Self-Hosted EC2 When:

1. **High volume**: > 10,000 requests/day where API costs exceed EC2
2. **Data privacy**: Cannot send data to third-party APIs
3. **Custom models**: Need fine-tuned or specialized models
4. **Predictable costs**: Fixed monthly budget regardless of usage
5. **Low latency critical**: Need < 500ms response times (GPU required)
6. **Offline operation**: Must work without internet

### Choose Cloud API (Bedrock) When:

1. **Variable/low traffic**: < 10,000 requests/day ✅ (your case)
2. **Zero maintenance**: Don't want infrastructure overhead ✅
3. **Rapid scaling**: Traffic can spike unpredictably
4. **Quality matters**: Claude generally outperforms open-source LLMs
5. **Cost optimization**: Pay only for what you use ✅
6. **Quick start**: Need to deploy immediately ✅

---

## Cost Projection for Your Use Case

### Scenario: 10 messages/day, growing to 50/day over 1 year

#### Year 1 Cost Comparison

| Month | Daily Msgs | EC2 CPU Cost | Bedrock Haiku Cost |
|-------|------------|--------------|-------------------|
| 1-3 | 10 | $98.58 | $0.09 |
| 4-6 | 20 | $98.58 | $0.18 |
| 7-9 | 35 | $98.58 | $0.32 |
| 10-12 | 50 | $98.58 | $0.45 |
| **Total** | | **$394.32** | **$1.04** |

**Savings with Bedrock: $393.28/year (99.7%)**

---

## Implementation Recommendation

For the Discord LeetCode Bot with 10 messages/day:

### Immediate Action: Switch to AWS Bedrock

```java
// Current (Ollama on EC2)
@Bean
public ChatClient chatClient(OllamaChatModel ollamaChatModel) {
    return ChatClient.builder(ollamaChatModel).build();
}

// Recommended (AWS Bedrock)
@Bean
public ChatClient chatClient(BedrockChatModel bedrockChatModel) {
    return ChatClient.builder(bedrockChatModel).build();
}
```

### Architecture Comparison

**Current (EC2)**:
```
Discord → EC2 (Spring Boot + JDA + Ollama) → PostgreSQL
              Always running: $33/month
```

**Recommended (Serverless)**:
```
Discord → API Gateway → Lambda → Bedrock + DynamoDB
              Pay per use: $0.03-0.50/month
```

### Migration Path

1. **Phase 1**: Keep EC2, swap Ollama for Bedrock API calls
   - Minimal code change
   - Immediate quality improvement
   - Cost: ~$32/month (EC2) + $0.03/month (Bedrock) = $32.03

2. **Phase 2**: Migrate to full serverless (Lambda + DynamoDB)
   - Zero EC2 costs
   - Cost: ~$5-10/month total (API Gateway, Lambda, DynamoDB, Bedrock)

---

## Appendix: Alternative Cloud LLM APIs

| Provider | Model | Input Cost (1K tokens) | Output Cost (1K tokens) | Quality |
|----------|-------|------------------------|------------------------|---------|
| AWS Bedrock | Claude 3 Haiku | $0.00025 | $0.00125 | Excellent |
| AWS Bedrock | Claude 3.5 Sonnet | $0.003 | $0.015 | Superior |
| OpenAI | GPT-4o-mini | $0.00015 | $0.0006 | Excellent |
| OpenAI | GPT-4o | $0.005 | $0.015 | Superior |
| Google | Gemini 1.5 Flash | $0.000075 | $0.0003 | Good |
| Anthropic Direct | Claude 3 Haiku | $0.00025 | $0.00125 | Excellent |

**Note**: OpenAI GPT-4o-mini and Google Gemini Flash are slightly cheaper than Claude Haiku but may require more prompt engineering for consistent JSON output.

---

## Conclusion

For a Discord bot processing 10 messages/day:

| Metric | Winner | Margin |
|--------|--------|--------|
| Cost | Bedrock | 99% cheaper |
| Latency | EC2 GPU | 3-4x faster (but $383/month) |
| Quality | Bedrock | Claude > Llama for instruction following |
| Maintenance | Bedrock | Zero vs ongoing |
| Scalability | Bedrock | Infinite vs manual |

**Final Recommendation**: Use **AWS Bedrock with Claude 3 Haiku**. At 10 messages/day, you'll pay approximately **$0.03/month** vs **$33/month** for EC2—a **99.9% cost reduction** with better quality and zero maintenance.
