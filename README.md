# AI Paper Benchmark

Benchmark comparing LLM-orchestrated backend operations against traditional Java/Spring Boot implementations across three enterprise scenarios: data retrieval, data normalization, and command execution.

## Prerequisites

- Java 17+
- Docker & Docker Compose (for PostgreSQL)
- [Ollama](https://ollama.ai/) running with models pulled:
  ```bash
  ollama pull mistral:7b
  ollama pull llama3.1:8b
  ollama pull qwen2.5:7b
  ```

## Setup

1. **Create `.env`** from the example:

   ```
   OLLAMA_BASE_URL=http://localhost:11434
   OLLAMA_MODELS=mistral:7b,llama3.1:8b,qwen2.5:7b

   POSTGRES_DB=aipaper
   POSTGRES_USER=aipaper
   POSTGRES_PASSWORD=aipaper
   ```

   Optionally add `GEMINI_API_KEY` and `GROQ_API_KEY` to enable remote LLM providers.

2. **Start PostgreSQL**:

   ```bash
   docker compose up -d
   ```

3. **Run the benchmark**:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark
   ```

   Results are written incrementally to `benchmark_results.csv`. The benchmark resumes automatically if interrupted.

## Scenarios

| Scenario | What it tests | Traditional approach | LLM approach |
|----------|---------------|---------------------|--------------|
| **Data Retrieval** | Tool calling to query a DB | JPA repository lookup | LLM invokes `findUserByEmail` tool via Spring AI |
| **Data Normalization** | Text processing & formatting | `DateTimeFormatter` chain + regex | LLM parses date/address from prompt |
| **Command Execution** | Tool calling with parameter extraction | Java validation + JPA persist | LLM extracts params, calls `bookMeeting` tool |

## Project Structure

```
src/main/java/com/aipaper/
├── benchmark/          # BenchmarkRunner, TestDataPool, CSV exporter
├── config/             # LLM client config, DB seeder
├── dto/                # Request/response records
├── entity/             # JPA entities (UserProfile, Meeting)
├── exception/          # Custom validation exceptions
├── repository/         # Spring Data JPA repositories
├── service/            # Traditional + LLM scenario implementations
├── tools/              # Spring AI @Tool classes (DB query, meeting booking)
└── validation/         # LLM response schema validator
```

## Configuration

Key settings in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `benchmark.iterations` | `100` | Iterations per model per scenario |
| `benchmark.ollama-models` | from `OLLAMA_MODELS` env | Comma-separated model list |
| `benchmark.gemini.enabled` | `false` | Enable Google Gemini |
| `benchmark.groq.enabled` | `false` | Enable Groq |

## Output

CSV with columns: `Model, Scenario, Accuracy, Latency_ms, TTFT_ms, Prompt_Tokens, Completion_Tokens, Error_Type`

See [methodology.md](methodology.md) for full benchmark methodology, metrics definitions, and results.

## Tech Stack

- Spring Boot 3.4.1
- Spring AI 1.0.0
- PostgreSQL 16
- Ollama (local LLM inference)
- Maven
