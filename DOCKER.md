# Docker Guide for LLM Proxy Java

This guide provides detailed instructions for deploying the LLM Proxy Java application using Docker.

## Prerequisites

- Docker installed on your system
- Docker Compose (optional, for easier deployment)

## Quick Start

1. Clone the repository:
   ```bash
   git clone https://github.com/amorin24/llmproxy-java.git
   cd llmproxy-java
   ```

2. Create a `.env` file with your API keys:
   ```
   OPENAI_API_KEY=your_openai_api_key
   GEMINI_API_KEY=your_gemini_api_key
   MISTRAL_API_KEY=your_mistral_api_key
   CLAUDE_API_KEY=your_claude_api_key
   ```

3. Build and run with Docker Compose:
   ```bash
   docker-compose up -d
   ```

4. Access the web UI at `http://localhost:8080`

## Manual Docker Deployment

### Building the Image

```bash
docker build -t llmproxy-java .
```

### Running the Container

```bash
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=your_openai_api_key \
  -e GEMINI_API_KEY=your_gemini_api_key \
  -e MISTRAL_API_KEY=your_mistral_api_key \
  -e CLAUDE_API_KEY=your_claude_api_key \
  llmproxy-java
```

## Configuration Options

You can configure the container using environment variables:

| Environment Variable | Description | Default |
|----------------------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key | - |
| `GEMINI_API_KEY` | Google Gemini API key | - |
| `MISTRAL_API_KEY` | Mistral API key | - |
| `CLAUDE_API_KEY` | Anthropic Claude API key | - |
| `JAVA_OPTS` | JVM options | `-Xms512m -Xmx1024m` |

## Health Checks

The Docker Compose configuration includes a health check that verifies the application is running correctly by checking the `/api/health` endpoint.

## Production Deployment

For production deployments, consider:

1. Using a reverse proxy like Nginx for SSL termination
2. Setting up proper logging with volume mounts
3. Implementing monitoring and alerting
4. Using Docker Swarm or Kubernetes for orchestration

Example production docker-compose.yml:

```yaml
version: '3.8'

services:
  llmproxy:
    build: .
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - GEMINI_API_KEY=${GEMINI_API_KEY}
      - MISTRAL_API_KEY=${MISTRAL_API_KEY}
      - CLAUDE_API_KEY=${CLAUDE_API_KEY}
      - JAVA_OPTS=-Xms1g -Xmx2g
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    volumes:
      - ./logs:/app/logs
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
```

## Troubleshooting

### Container fails to start

Check the logs for errors:

```bash
docker logs llmproxy-java
```

### API keys not being recognized

Ensure environment variables are correctly passed to the container. You can verify with:

```bash
docker exec llmproxy-java env | grep API_KEY
```

### Memory issues

Adjust the JVM memory settings using the `JAVA_OPTS` environment variable:

```bash
docker run -e JAVA_OPTS="-Xms1g -Xmx2g" llmproxy-java
```
