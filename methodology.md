# Benchmark Methodology

## Overview

This benchmark compares **LLM-orchestrated backend operations** against a **traditional hardcoded Java/Spring Boot backend** across three representative enterprise scenarios. The goal is to measure accuracy, latency, and token efficiency of different LLM models when used as backend orchestration layers.

## Architecture

- **Framework**: Spring Boot 3.4.1, Java 17
- **LLM Integration**: Spring AI 1.0.0 (`ChatClient`, `ChatModel`, `@Tool` function calling)
- **Database**: PostgreSQL (Docker Compose), Spring Data JPA
- **LLM Provider**: Ollama (local, via Windows host at `172.31.112.1:11434`)
- **Benchmark Profile**: Activated via `--spring.profiles.active=benchmark`

## Test Scenarios

### 1. Data Retrieval (Tool Calling)

The LLM receives a user email and must use the `findUserByEmail` tool to query the database, then return a structured JSON response.

- **Input**: Random email from 5 seeded users (e.g., `alice.johnson@example.com`)
- **LLM prompt**: Instructs the model to call the tool and return `{email, firstName, lastName, phone, address}`
- **Validation**: JSON schema check (valid email format, non-blank required fields)
- **Traditional baseline**: Direct JPA repository query, returns `UserProfileResult`

This scenario tests the model's ability to correctly invoke function calling APIs and return structured data.

### 2. Data Normalization (Text Processing)

The LLM receives a raw date string and a raw address string, and must normalize both into standard formats.

- **Input**: Random selection from 8 test cases with varying date formats
- **LLM prompt**: Convert date to ISO-8601 (`yyyy-MM-dd`), expand address abbreviations, apply title case
- **Validation**: JSON schema check + exact match against expected date + valid ISO-8601 format
- **Traditional baseline**: Java `DateTimeFormatter` chain with case-insensitive parsing and regex-based address normalization

#### Normalization Test Cases

| Format | Example | Expected |
|--------|---------|----------|
| ISO-8601 | `2024-01-05` | `2024-01-05` |
| US date | `12/25/2022` | `2022-12-25` |
| US date | `01/15/2024` | `2024-01-15` |
| US dashed | `03-22-2023` | `2023-03-22` |
| Month name | `March 15 2023` | `2023-03-15` |
| Ordinal | `Jan 5th, 2024` | `2024-01-05` |
| Ordinal | `Nov 11th 2023` | `2023-11-11` |
| Leap day | `2024-02-29` | `2024-02-29` |

Each case includes an expected normalized date for correctness verification.

### 3. Command Execution (Tool Calling + Validation)

The LLM receives meeting booking details and must call the `bookMeeting` tool, then return the structured result.

- **Input**: Random selection from 5 valid meeting requests
- **LLM prompt**: Book the meeting using the tool and return `{success, meetingId, message}`
- **Validation**: JSON schema check (success boolean present, meetingId non-null on success)
- **Traditional baseline**: Explicit Java validation (email regex, time ordering, required fields) + JPA persistence

All meeting test cases use valid parameters (proper emails, correct date/time ordering, non-empty titles). This tests the model's ability to extract parameters from natural language, invoke a tool correctly, and return the structured result.

## Metrics

Each iteration produces one `BenchmarkResult` row with:

| Metric | Description |
|--------|-------------|
| `Model` | Provider/model identifier (e.g., `Ollama/mistral:7b`, `Traditional`) |
| `Scenario` | One of: `DataRetrieval`, `DataNormalization`, `CommandExecution` |
| `Accuracy` | `true` if response passes all validation checks, `false` otherwise |
| `Latency_ms` | Wall-clock time from request start to response parsed (via `System.nanoTime()`) |
| `TTFT_ms` | Time to first token (`-1` — reserved for future streaming implementation) |
| `Prompt_Tokens` | Tokens in the prompt (from Ollama metadata) |
| `Completion_Tokens` | Tokens in the completion (from Ollama metadata) |
| `Error_Type` | Exception class name on failure (e.g., `MismatchedInputException`, `TimeoutException`) |

### Accuracy Definition

A result is marked **accurate** only if ALL of the following pass:

1. **Parseable**: Raw LLM response deserializes into the expected Java record via Jackson
2. **Schema-valid**: All required fields are present and well-formed (per `LlmResponseValidator`)
3. **Correct**: Scenario-specific correctness checks:
   - DataRetrieval: email format valid, names non-blank
   - DataNormalization: `normalizedDate` matches expected ISO-8601 date exactly
   - CommandExecution: all required fields present and well-formed

### Latency Measurement

- Measured with `System.nanoTime()` for nanosecond precision
- Includes full round-trip: prompt serialization → LLM inference → response parsing
- For tool-calling scenarios, includes tool execution time (DB queries)
- Traditional baseline latency includes only Java code execution + DB query

## Execution Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| `benchmark.iterations` | 100 | Iterations per model per scenario |
| `benchmark.output-file` | `benchmark_results.csv` | Output CSV path |
| `benchmark.ollama-models` | `mistral:7b,llama3.1:8b,qwen2.5:7b` | Ollama models to benchmark |
| `CALL_TIMEOUT_SECONDS` | 30 | Max seconds per LLM call before timeout |
| `EARLY_STOP_THRESHOLD` | 20 | Consecutive failures before skipping a scenario |
| `DATA_POOL_SEED` | 42 | Fixed random seed for reproducibility |

## Input Randomization

- **Test data pool**: `TestDataPool` class with a fixed random seed (`42`) for reproducibility
- **Per iteration**: Each iteration draws a random email, normalization case, and meeting case
- **Across models**: All models receive the same random sequence (same seed, same order) for fair comparison
- **Pool sizes**: 5 emails × 8 normalization cases × 5 meeting cases

With 100 iterations, each normalization case is sampled ~12.5 times on average and each meeting case ~20 times. Since temperature is 0.0, accuracy is deterministic per input — repeated samples produce identical results. The iteration count provides stable latency statistics.

## LLM Configuration

- **Temperature**: `0.0` (deterministic — accuracy reflects model capability, not sampling luck)
- **System prompt**: `"You are a precise data API. Always respond with ONLY a raw JSON object. Never include markdown formatting, code fences, or explanations."`
- **Response handling**: Raw text response → strip markdown fences → Jackson deserialization → validation
- **Tool definitions**: Spring AI `@Tool` annotation with `@ToolParam` descriptions

## Resilience Features

### Connectivity Check

Before benchmarking each Ollama model, a 10-second HTTP ping is sent to the Ollama `/api/tags` endpoint. If Ollama is unresponsive, the model is skipped entirely rather than waiting for timeouts.

### Per-Call Timeout

Each LLM call runs on a dedicated daemon thread with a 30-second timeout. If the call doesn't return in time (e.g., due to a tool-calling loop), it is cancelled and recorded as a `TimeoutException` failure.

### Incremental CSV Writing

Results are written to CSV one row at a time with immediate flush. If the process crashes, all completed iterations are preserved.

### Resume on Restart

On startup, the benchmark reads the existing CSV to count completed iterations per model/scenario. It resumes from where it left off.

### Early-Stop Mechanism

If a model fails a scenario **20 consecutive times**, that scenario is skipped for the remaining iterations. If all 3 scenarios are early-stopped, the model is skipped entirely.

## Execution Order

1. **Traditional** (Java baseline — runs first, no external dependencies)
2. **Ollama models** (from `OLLAMA_MODELS` env, e.g., `mistral:7b,llama3.1:8b,qwen2.5:7b`)
3. **Gemini** (if enabled, with rate limiting)
4. **Groq** (if enabled, with rate limiting)

Each model runs all 3 scenarios interleaved for N iterations: iteration 1 (retrieval + normalization + command), iteration 2, etc.

## Running the Benchmark

```bash
# Start PostgreSQL
docker compose up -d

# Run benchmark
./mvnw spring-boot:run -Dspring-boot.run.profiles=benchmark
```

## Output Format

CSV file (`benchmark_results.csv`) with header:

```
Model,Scenario,Accuracy,Latency_ms,TTFT_ms,Prompt_Tokens,Completion_Tokens,Error_Type
```

Ready for import into R, Python (pandas), or LaTeX table generation.

## Results Summary

Benchmark run: 100 iterations per model, 4 models × 3 scenarios = 1129 total data points.

### Accuracy

| Model | DataRetrieval | DataNormalization | CommandExecution |
|-------|:---:|:---:|:---:|
| Traditional | 100% (100/100) | 100% (100/100) | 100% (100/100) |
| Ollama/mistral:7b | 7% (2/29, early-stopped) | 100% (100/100) | 100% (100/100) |
| Ollama/llama3.1:8b | 100% (100/100) | 100% (100/100) | 100% (100/100) |
| Ollama/qwen2.5:7b | 100% (100/100) | 100% (100/100) | 100% (100/100) |

- `mistral:7b` consistently fails DataRetrieval with `MismatchedInputException` (returns wrong JSON structure from tool calls)

### Average Latency

| Model | DataRetrieval | DataNormalization | CommandExecution |
|-------|---:|---:|---:|
| Traditional | 1.1ms | 0.2ms | 1.8ms |
| Ollama/mistral:7b | 346ms | 289ms | 263ms |
| Ollama/llama3.1:8b | 492ms | 213ms | 601ms |
| Ollama/qwen2.5:7b | 559ms | 288ms | 787ms |

- Traditional is **100–4000× faster** than LLM approaches
- Latency generally increases with task complexity (tool-calling scenarios are slower)

### Average Token Usage

| Model | DataRetrieval | DataNormalization | CommandExecution |
|-------|---:|---:|---:|
| Ollama/mistral:7b | 202 + 59 | 205 + 55 | 381 + 47 |
| Ollama/llama3.1:8b | 464 + 74 | 172 + 34 | 669 + 105 |
| Ollama/qwen2.5:7b | 573 + 76 | 181 + 43 | 890 + 118 |

(Format: prompt tokens + completion tokens)

## Reproducibility

- Fixed random seed (`42`) ensures identical input sequences across runs
- Temperature `0.0` ensures deterministic LLM outputs for identical prompts
- Same seed + same model version + same Ollama configuration = identical results
- CSV resume mechanism means interrupted runs produce the same final dataset
