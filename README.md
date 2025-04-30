# LLM Proxy System (Java)

A Spring Boot-based proxy system for routing requests to multiple Large Language Models (LLMs) including OpenAI, Gemini, Mistral, and Claude.

## Features

- Dynamic routing to multiple LLM providers
- Model selection based on task type and availability
- Comprehensive error handling with retries and fallbacks
- Detailed structured logging for requests, responses, and errors
- Caching for frequently requested queries
- Simple web UI for testing and interaction

## Error Handling Features

- Timeout handling with automatic retries
- Rate-limiting detection and handling
- Fallback to alternative models when errors occur
- Graceful handling of API errors with user-friendly messages
- Exponential backoff with jitter for retries

## Logging Features

- Structured JSON logging for easy analysis
- Detailed request logging (model, timestamp, query)
- Comprehensive response logging (model, response time, tokens, status code)
- Error logging with error types and details
- Request ID tracking across the system
- Token usage tracking and logging

## Prerequisites

- Java 17 or higher
- Maven

## Configuration

Create a `.env` file in the root directory with the following variables:

```
# API Keys for LLM providers
OPENAI_API_KEY=your_openai_api_key
GEMINI_API_KEY=your_gemini_api_key
MISTRAL_API_KEY=your_mistral_api_key
CLAUDE_API_KEY=your_claude_api_key
```

## Running Locally

```bash
# Build and run
mvn clean install
mvn spring-boot:run
```

## API Endpoints

- `POST /api/query`: Send a query to an LLM
  - Request body:
    ```json
    {
      "query": "Your query text",
      "model": "OPENAI|GEMINI|MISTRAL|CLAUDE", // Optional
      "modelVersion": "gpt-3.5-turbo|gemini-pro|...", // Optional
      "taskType": "TEXT_GENERATION|SUMMARIZATION|SENTIMENT_ANALYSIS|QUESTION_ANSWERING", // Optional
      "requestId": "optional-request-id-for-tracking" // Optional
    }
    ```

- `GET /api/status`: Check the status of all LLM providers

## Web UI

Access the web UI at `http://localhost:8080`

## Token Usage Tracking

The LLM Proxy System tracks token usage for all LLM providers:

- **Detailed Token Breakdown**: Tracks input tokens, output tokens, and total tokens for each request
- **Provider-Specific Implementation**:
  - OpenAI: Uses the detailed token information provided in the API response
  - Mistral: Uses the detailed token information provided in the API response
  - Claude: Uses the input and output token counts from the API response
  - Gemini: Uses token information when available, falls back to estimation
- **Token Estimation**: For providers with limited token information, the system estimates token usage based on input/output text length
- **UI Display**: Token usage is displayed in a dedicated section in the web UI
- **Logging**: Token usage is included in structured logs for monitoring and analysis

## Architecture

The LLM Proxy System is built with a modular architecture:

- **Configuration**: Environment variables for API keys and settings
- **Models**: Data structures for requests and responses
- **Exceptions**: Standardized error types and handling
- **Retry**: Configurable retry mechanism with exponential backoff
- **Caching**: In-memory caching for frequently requested queries
- **Logging**: Structured logging for requests, responses, and errors
- **LLM Clients**: Separate clients for each LLM provider with error handling
- **Router**: Dynamic routing based on task type and availability with fallbacks
- **API Controllers**: RESTful API endpoints for queries and status
- **Web UI**: Simple interface for testing and interaction

## Testing

The system includes comprehensive unit and functional tests:

- **Unit Tests**: Test individual components in isolation
  - LLM clients
  - Router service
  - Cache service
  - Rate limiter
  - Token estimator

- **Functional Tests**: Test the integration between components
  - API endpoints
  - End-to-end flow

- **Integration Tests**: Test interactions with external services
  - LLM API interactions (using WireMock)

Run the tests with:

```bash
mvn test
```

## Development

To contribute to this project:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## License

MIT
