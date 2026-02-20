Phase 1: Infrastructure & Orchestration Setup
This prompt establishes the foundation, getting your local environment and API routers configured using Spring AI.

Prompt for Cursor:

Act as an expert Java/Spring Boot developer. Initialize a new Spring Boot 3.3 project using Java 17.
We need to orchestrate three different LLM providers for benchmarking: a local Ollama instance, Google Gemini, and Groq (via their OpenAI-compatible endpoint).
Please generate:

A docker-compose.yml that sets up PostgreSQL and an Ollama container (configured to automatically pull the llama3 model on startup).

The pom.xml including spring-ai-bom, spring-boot-starter-data-jpa, and spring-boot-starter-web.

An LlmRoutingService interface and its implementations using Spring AI's ChatClient to dynamically route incoming prompts to Ollama, Gemini, or Groq based on application properties. Ensure connection pooling and timeouts are optimized for testing.

Phase 2: Implementing the Scenarios
The original paper evaluated three specific tasks: fetching user profiles (Data Retrieval), standardizing inputs (Data Normalization), and executing a "book_meeting" function (Command Execution). This prompt ensures Cursor implements both the hardcoded and LLM-driven versions for comparison.

Prompt for Cursor:

Now, implement the core business logic. We are replicating three specific test scenarios:

Data Retrieval: Fetching user profiles by email.

Data Normalization: Standardizing inconsistent dates and addresses.

Command Execution: A 'book_meeting' function involving parameter validation and database insertions.

For each scenario, generate two distinct service implementations:

TraditionalBackendService: Uses strictly hardcoded Java logic and direct PostgreSQL queries/transactions.

LlmOrchestrationService: Uses Spring AI's structured output and function calling (tool calling) to execute the exact same tasks. You MUST implement strict JSON schema validation on all LLM outputs to catch malformed responses or parameter mismatches, throwing specific custom exceptions when they fail.

Phase 3: The Benchmarking Engine & CSV Export
To get reliable data, we need high iteration counts and granular metrics. This prompt builds the automated test runner and structures the output so it can easily drop straight into your LaTeX pgfplots or tables for the final paper.

Prompt for Cursor:

Generate a BenchmarkRunner component that executes automated tests to compare the Traditional vs. LLM approaches.
Requirements:

Create a test loop that runs each of the 3 scenarios 1,000 times for each provider (Ollama, Gemini, Groq) and the Traditional service. Use a warmed-up JVM to avoid cold-start bias.

Track the following metrics for every execution using System.nanoTime(): Accuracy (boolean success based on schema validation), Total Latency (ms), Time-to-First-Token (TTFT, if supported by the provider's streaming API), and Token Usage (Prompt vs. Completion).

Export the results directly into a flat benchmark_results.csv file using a BufferedWriter. Format the CSV columns exactly like this: Model,Scenario,Accuracy,Latency_ms,TTFT_ms,Prompt_Tokens,Completion_Tokens,Error_Type. Ensure the formatting is completely clean so it can be parsed directly by LaTeX for data visualization.