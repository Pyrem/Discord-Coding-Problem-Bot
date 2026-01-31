# Efficient LLM Deployment Options for AWS

This guide covers optimized deployment strategies for the Llama 3.2 model on AWS, focusing on cost-efficiency and performance.

## Current Architecture Issues

**Problems with basic Ollama deployment:**
- CPU-only inference is slow (~5-10 seconds per request)
- Memory-intensive (2GB+ for llama3.2)
- Not cost-effective at scale
- Cold start issues in serverless environments

---

## Option 1: GPU-Accelerated EC2 Instance (Best Performance)

### Architecture
```
┌─────────────────────────────────────────┐
│   EC2 g4dn.xlarge (GPU)                 │
│  ┌───────────────────────────────────┐  │
│  │ Ollama with CUDA support          │  │
│  │ - llama3.2 on GPU                 │  │
│  │ - Inference: ~500ms               │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
         ↑
         │ HTTP API
         │
┌─────────────────────────────────────────┐
│   EC2 t3.small (Bot + DB)               │
│  ┌───────────────────────────────────┐  │
│  │ Discord Bot + PostgreSQL          │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### Cost & Performance
- **Instance**: g4dn.xlarge @ $0.526/hour = ~$384/month
- **Inference Speed**: ~500ms (10-20x faster than CPU)
- **Best for**: High-volume production (>1000 requests/day)

### Implementation

**1. Launch GPU instance:**
```bash
aws ec2 run-instances \
  --image-id ami-0c55b159cbfafe1f0 \
  --instance-type g4dn.xlarge \
  --key-name your-key \
  --security-group-ids sg-your-sg \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=ollama-gpu}]'
```

**2. Install NVIDIA drivers and Docker:**
```bash
# SSH into instance
ssh -i your-key.pem ec2-user@gpu-instance-ip

# Install NVIDIA drivers
sudo yum install -y gcc kernel-devel-$(uname -r)
aws s3 cp --recursive s3://ec2-linux-nvidia-drivers/latest/ .
sudo chmod +x NVIDIA-Linux-x86_64*.run
sudo ./NVIDIA-Linux-x86_64*.run

# Install Docker with NVIDIA runtime
distribution=$(. /etc/os-release;echo $ID$VERSION_ID)
curl -s -L https://nvidia.github.io/nvidia-docker/$distribution/nvidia-docker.repo | \
  sudo tee /etc/yum.repos.d/nvidia-docker.repo

sudo yum install -y nvidia-docker2
sudo systemctl restart docker
```

**3. Run Ollama with GPU:**
```bash
docker run -d \
  --gpus=all \
  -v /data/ollama:/root/.ollama \
  -p 11434:11434 \
  --name ollama \
  --restart unless-stopped \
  ollama/ollama:latest

# Pull model
docker exec -it ollama ollama pull llama3.2

# Verify GPU usage
nvidia-smi
```

**4. Configure bot to use remote Ollama:**
```properties
# In application-ec2.properties
spring.ai.ollama.base-url=http://gpu-instance-private-ip:11434
```

---

## Option 2: AWS Bedrock (Managed, No Infrastructure)

### Architecture
```
┌─────────────────────────────────────────┐
│   Your Application (Discord Bot)       │
│  ┌───────────────────────────────────┐  │
│  │ AWS Bedrock Client                │  │
│  │ - Uses Claude or Llama via API    │  │
│  │ - No model management needed      │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────┐
│   AWS Bedrock (Managed)                 │
│   - Pay per token                       │
│   - Auto-scaling                        │
│   - Multiple models available           │
└─────────────────────────────────────────┘
```

### Cost & Performance
- **Pricing**: ~$0.001 per 1000 input tokens, ~$0.003 per 1000 output tokens
- **Example**: 10,000 requests/month × 100 tokens avg = ~$4/month
- **Latency**: ~1-2 seconds
- **Best for**: Variable workloads, minimal maintenance

### Implementation

**1. Update dependencies in pom.xml:**
```xml
<!-- Replace Spring AI Ollama with Bedrock -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bedrock-spring-boot-starter</artifactId>
</dependency>
```

**2. Update configuration:**
```properties
# application-ec2.properties
spring.ai.bedrock.aws.region=us-east-1
spring.ai.bedrock.anthropic.chat.enabled=true
spring.ai.bedrock.anthropic.chat.model=anthropic.claude-3-haiku-20240307-v1:0

# Or use Llama via Bedrock (if available)
# spring.ai.bedrock.llama.chat.enabled=true
# spring.ai.bedrock.llama.chat.model=meta.llama3-8b-instruct-v1:0
```

**3. Update RequestParserService:**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestParserService {

    private final ChatClient.Builder chatClientBuilder;

    // No changes needed - Spring AI abstracts the model
    public CompanyProblemRequest parseRequest(String message) {
        ChatClient chatClient = chatClientBuilder.build();

        String response = chatClient.prompt(prompt)
            .call()
            .content();

        return parseJsonResponse(response);
    }
}
```

**4. IAM permissions:**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "bedrock:InvokeModel"
      ],
      "Resource": "arn:aws:bedrock:*::foundation-model/*"
    }
  ]
}
```

---

## Option 3: Amazon SageMaker Endpoint (Custom Model Hosting)

### Architecture
```
┌─────────────────────────────────────────┐
│   Discord Bot Application               │
└─────────────────┬───────────────────────┘
                  │
                  ↓ HTTPS API
┌─────────────────────────────────────────┐
│   SageMaker Serverless Inference        │
│  ┌───────────────────────────────────┐  │
│  │ llama3.2 Model                    │  │
│  │ - Auto-scaling (0 to N)           │  │
│  │ - Pay per inference               │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### Cost & Performance
- **Serverless**: $0.20 per hour per 1024 MB memory + $0.00002 per millisecond
- **Real-time endpoint**: Starting at ~$0.05/hour for smallest instance
- **Best for**: Moderate traffic with bursts

### Implementation

**1. Create model artifacts:**
```python
# On local machine with model
import boto3
import tarfile

# Package model for SageMaker
with tarfile.open('model.tar.gz', 'w:gz') as tar:
    tar.add('model/', arcname='.')

# Upload to S3
s3 = boto3.client('s3')
s3.upload_file('model.tar.gz', 'your-bucket', 'llama3.2/model.tar.gz')
```

**2. Create SageMaker endpoint (using AWS CLI):**
```bash
# Create model
aws sagemaker create-model \
  --model-name llama32-model \
  --primary-container Image=763104351884.dkr.ecr.us-east-1.amazonaws.com/huggingface-pytorch-inference:2.0.0-transformers4.28.1-cpu-py310-ubuntu20.04,\
ModelDataUrl=s3://your-bucket/llama3.2/model.tar.gz \
  --execution-role-arn arn:aws:iam::account:role/SageMakerRole

# Create endpoint config (serverless)
aws sagemaker create-endpoint-config \
  --endpoint-config-name llama32-serverless-config \
  --production-variants \
    VariantName=AllTraffic,ModelName=llama32-model,ServerlessConfig={MemorySizeInMB=4096,MaxConcurrency=5}

# Create endpoint
aws sagemaker create-endpoint \
  --endpoint-name llama32-endpoint \
  --endpoint-config-name llama32-serverless-config
```

**3. Update application to use SageMaker:**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SageMakerRequestParserService {

    private final SageMakerRuntimeClient sagemakerClient;

    public CompanyProblemRequest parseRequest(String message) {
        String prompt = createPrompt(message);

        InvokeEndpointRequest request = InvokeEndpointRequest.builder()
            .endpointName("llama32-endpoint")
            .contentType("application/json")
            .body(SdkBytes.fromUtf8String(
                String.format("{\"inputs\": \"%s\"}", prompt)
            ))
            .build();

        InvokeEndpointResponse response = sagemakerClient.invokeEndpoint(request);
        String responseBody = response.body().asUtf8String();

        return parseJsonResponse(responseBody);
    }
}
```

---

## Option 4: Spot Instances for GPU (70% Cost Savings)

### Architecture
Same as Option 1, but using EC2 Spot Instances

### Cost & Performance
- **Instance**: g4dn.xlarge spot @ ~$0.158/hour = ~$115/month (70% cheaper!)
- **Trade-off**: Can be interrupted with 2-minute notice
- **Best for**: Non-critical workloads, batch processing

### Implementation

**1. Launch spot instance:**
```bash
aws ec2 request-spot-instances \
  --spot-price "0.30" \
  --instance-count 1 \
  --type "persistent" \
  --launch-specification '{
    "ImageId": "ami-0c55b159cbfafe1f0",
    "InstanceType": "g4dn.xlarge",
    "KeyName": "your-key",
    "SecurityGroupIds": ["sg-your-sg"],
    "UserData": "base64-encoded-startup-script"
  }'
```

**2. Handle spot interruptions:**
```bash
# Create systemd service that monitors spot termination
cat > /etc/systemd/system/spot-monitor.service << 'EOF'
[Unit]
Description=Spot Instance Termination Monitor

[Service]
Type=simple
ExecStart=/usr/local/bin/spot-monitor.sh
Restart=always

[Install]
WantedBy=multi-user.target
EOF

# Monitoring script
cat > /usr/local/bin/spot-monitor.sh << 'EOF'
#!/bin/bash
while true; do
  if curl -s http://169.254.169.254/latest/meta-data/spot/instance-action | grep -q terminate; then
    echo "Spot termination notice received, gracefully shutting down..."
    docker-compose down
  fi
  sleep 5
done
EOF

chmod +x /usr/local/bin/spot-monitor.sh
systemctl enable spot-monitor
systemctl start spot-monitor
```

---

## Option 5: Quantized Model (Faster & Cheaper)

### Use smaller model variants

**Current**: llama3.2 (3B parameters) → ~2GB RAM, ~5s inference

**Alternative models:**
1. **llama3.2:1b** - 1B parameters, ~1GB RAM, ~2s inference
2. **phi3:mini** - Microsoft's 3.8B, optimized for efficiency
3. **tinyllama** - 1.1B parameters, extremely fast

### Implementation

Simply change the model in configuration:

```properties
# application.properties
spring.ai.ollama.chat.options.model=llama3.2:1b
# or
spring.ai.ollama.chat.options.model=phi3:mini
```

Then pull the smaller model:
```bash
docker exec -it ollama ollama pull llama3.2:1b
```

**Cost savings**: Can run on t3.small (~$15/month) instead of t3.medium

---

## Option 6: AWS Lambda + Bedrock (Serverless)

### Architecture - Serverless Everything
```
Discord → API Gateway → Lambda (Bot Logic) → Bedrock (LLM)
                              ↓
                        RDS Proxy → Aurora Serverless
```

### Cost & Performance
- **Lambda**: $0.20 per 1M requests + compute time
- **Bedrock**: ~$0.001 per request
- **Aurora Serverless v2**: Scales to zero, ~$0.12/hour when active
- **Total for 10k requests/month**: ~$5-10/month
- **Best for**: Extremely variable workloads

### Implementation

This requires significant refactoring to make the bot stateless and event-driven. Would need to:
1. Convert Discord bot to webhook-based (not WebSocket)
2. Refactor as Lambda handlers
3. Use Aurora Serverless v2 instead of containerized PostgreSQL

---

## Recommendation Matrix

| Use Case | Recommended Option | Monthly Cost | Latency |
|----------|-------------------|--------------|---------|
| **Development/Testing** | Ollama on t3.medium | $30 | 5-10s |
| **Low traffic (<100 req/day)** | Bedrock | $4-10 | 1-2s |
| **Medium traffic (100-1000/day)** | Spot GPU g4dn.xlarge | $115 | 500ms |
| **High traffic (>1000/day)** | On-demand GPU g4dn.xlarge | $384 | 500ms |
| **Variable/Burst traffic** | SageMaker Serverless | $20-60 | 2-5s |
| **Cost-optimized** | Quantized model on t3.small | $15 | 2-3s |

---

## Best Choice for Your Use Case

For a **Discord bot with typical usage** (10-100 requests/day):

**Recommended: AWS Bedrock with Claude 3 Haiku**
- ✅ Zero infrastructure
- ✅ ~$5-10/month
- ✅ 1-2 second latency
- ✅ Better accuracy than llama3.2
- ✅ Automatic scaling
- ✅ No model management

**Implementation Priority:**
1. Start with Ollama on t3.medium (easy, works now)
2. Switch to quantized model (llama3.2:1b) for 2x speed boost
3. Migrate to Bedrock when traffic increases

---

## Code Changes for Bedrock Migration

### 1. Update pom.xml
```xml
<!-- Remove -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
</dependency>

<!-- Add -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bedrock-ai-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>bedrock-runtime</artifactId>
</dependency>
```

### 2. Update application.properties
```properties
# Remove Ollama config
# spring.ai.ollama.base-url=...

# Add Bedrock config
spring.ai.bedrock.aws.region=${AWS_REGION:us-east-1}
spring.ai.bedrock.anthropic.chat.enabled=true
spring.ai.bedrock.anthropic.chat.model=anthropic.claude-3-haiku-20240307-v1:0
spring.ai.bedrock.anthropic.chat.options.temperature=0.3
spring.ai.bedrock.anthropic.chat.options.max-tokens=500
```

### 3. Update SpringAiConfig.java
```java
@Configuration
public class SpringAiConfig {

    @Bean
    public BedrockAnthropicChatModel chatModel(
            @Value("${spring.ai.bedrock.aws.region}") String region) {

        return BedrockAnthropicChatModel.builder()
            .withRegion(Region.of(region))
            .withModel("anthropic.claude-3-haiku-20240307-v1:0")
            .withTemperature(0.3)
            .build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
```

### 4. No changes needed to RequestParserService!

The beauty of Spring AI is that `RequestParserService` doesn't need any changes - it abstracts the underlying model.

---

## Performance Comparison

**Parsing "Microsoft last 30 days":**

| Option | Latency | Cost/1k requests |
|--------|---------|------------------|
| Ollama CPU (t3.medium) | 5-10s | $0.04 |
| Ollama GPU (g4dn.xlarge) | 500ms | $0.53 |
| Bedrock Claude Haiku | 1-2s | $1.00 |
| SageMaker Serverless | 2-5s | $0.50 |
| Quantized llama3.2:1b | 2-3s | $0.02 |

---

## Next Steps

1. **Immediate**: Use quantized model for 2x speed boost with zero cost increase
2. **Short-term**: Evaluate Bedrock for 1-2 week trial
3. **Long-term**: If traffic grows, move to GPU spot instances

Want me to implement any of these options for you?
