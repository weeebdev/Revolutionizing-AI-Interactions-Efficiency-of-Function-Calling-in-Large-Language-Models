package com.aipaper.benchmark;

import com.aipaper.benchmark.TestDataPool.MeetingTestCase;
import com.aipaper.benchmark.TestDataPool.NormalizationTestCase;
import com.aipaper.dto.MeetingBookingRequest;
import com.aipaper.dto.MeetingBookingResult;
import com.aipaper.dto.NormalizationRequest;
import com.aipaper.dto.NormalizedDataResult;
import com.aipaper.dto.UserProfileResult;
import com.aipaper.exception.LlmResponseValidationException;
import com.aipaper.exception.ParameterMismatchException;
import com.aipaper.repository.MeetingRepository;
import com.aipaper.repository.UserProfileRepository;
import com.aipaper.service.LlmProvider;
import com.aipaper.service.LlmRoutingService;
import com.aipaper.service.scenario.traditional.TraditionalCommandExecutionService;
import com.aipaper.service.scenario.traditional.TraditionalDataNormalizationService;
import com.aipaper.service.scenario.traditional.TraditionalDataRetrievalService;
import com.aipaper.tools.MeetingBookingTool;
import com.aipaper.tools.UserProfileQueryTool;
import com.aipaper.validation.LlmResponseValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
@Profile("benchmark")
public class BenchmarkRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private static final String SCENARIO_RETRIEVAL = "DataRetrieval";
    private static final String SCENARIO_NORMALIZATION = "DataNormalization";
    private static final String SCENARIO_COMMAND = "CommandExecution";
    private static final String MODEL_TRADITIONAL = "Traditional";
    private static final String[] ALL_SCENARIOS = {SCENARIO_RETRIEVAL, SCENARIO_NORMALIZATION, SCENARIO_COMMAND};
    private static final int EARLY_STOP_THRESHOLD = 20;
    private static final long DATA_POOL_SEED = 42;
    private static final int CALL_TIMEOUT_SECONDS = 30;

    private static final String JSON_SYSTEM =
            "You are a precise data API. Always respond with ONLY a raw JSON object. " +
            "Never include markdown formatting, code fences, or explanations.";

    @Value("${benchmark.iterations:100}")
    private int defaultIterations;

    @Value("${benchmark.output-file:benchmark_results.csv}")
    private String outputFile;

    @Value("${benchmark.ollama-models:}")
    private String ollamaModelsRaw;

    @Value("${llm.ollama.base-url:http://172.31.112.1:11434}")
    private String ollamaBaseUrl;

    @Value("${benchmark.gemini.enabled:false}")
    private boolean geminiEnabled;

    @Value("${benchmark.gemini.iterations:70}")
    private int geminiIterations;

    @Value("${benchmark.gemini.delay-ms:6500}")
    private long geminiDelayMs;

    @Value("${benchmark.groq.enabled:false}")
    private boolean groqEnabled;

    @Value("${benchmark.groq.iterations:300}")
    private int groqIterations;

    @Value("${benchmark.groq.delay-ms:2200}")
    private long groqDelayMs;

    private final TraditionalDataRetrievalService traditionalRetrieval;
    private final TraditionalDataNormalizationService traditionalNormalization;
    private final TraditionalCommandExecutionService traditionalCommand;
    private final LlmRoutingService routingService;
    private final UserProfileRepository userProfileRepo;
    private final MeetingRepository meetingRepo;
    private final LlmResponseValidator validator;
    private final ObjectMapper objectMapper;

    public BenchmarkRunner(TraditionalDataRetrievalService traditionalRetrieval,
                           TraditionalDataNormalizationService traditionalNormalization,
                           TraditionalCommandExecutionService traditionalCommand,
                           LlmRoutingService routingService,
                           UserProfileRepository userProfileRepo,
                           MeetingRepository meetingRepo,
                           LlmResponseValidator validator,
                           ObjectMapper objectMapper) {
        this.traditionalRetrieval = traditionalRetrieval;
        this.traditionalNormalization = traditionalNormalization;
        this.traditionalCommand = traditionalCommand;
        this.routingService = routingService;
        this.userProfileRepo = userProfileRepo;
        this.meetingRepo = meetingRepo;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> ollamaModels = parseOllamaModels();
        TestDataPool dataPool = new TestDataPool(DATA_POOL_SEED);

        log.info("========================================");
        log.info("  BENCHMARK STARTING");
        log.info("  Iterations: {}", defaultIterations);
        log.info("  Ollama models: {}", ollamaModels);
        log.info("  Output: {}", outputFile);
        log.info("========================================");

        Map<String, Integer> completed = BenchmarkCsvExporter.loadCompletedCounts(outputFile);
        if (!completed.isEmpty()) {
            log.info("  Resume detected:");
            completed.forEach((k, v) -> log.info("    {} -> {}", k, v));
        }

        List<ModelRunConfig> runOrder = buildRunOrder(ollamaModels);
        List<BenchmarkResult> sessionResults = new ArrayList<>();

        try (BenchmarkCsvExporter csv = BenchmarkCsvExporter.open(outputFile)) {
            for (ModelRunConfig cfg : runOrder) {
                runModelBenchmark(cfg, csv, completed, sessionResults, dataPool);
            }
        }

        logSummary(sessionResults);
        log.info("========================================");
        log.info("  BENCHMARK COMPLETE — {} results", sessionResults.size());
        log.info("========================================");
    }

    private List<String> parseOllamaModels() {
        if (ollamaModelsRaw == null || ollamaModelsRaw.isBlank()) return List.of();
        return Arrays.stream(ollamaModelsRaw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private List<ModelRunConfig> buildRunOrder(List<String> ollamaModels) {
        List<ModelRunConfig> order = new ArrayList<>();

        // Traditional first — always works, no external dependencies
        order.add(new ModelRunConfig(MODEL_TRADITIONAL, null, defaultIterations, 0));

        for (String model : ollamaModels) {
            ChatClient client = createOllamaClient(model);
            order.add(new ModelRunConfig("Ollama/" + model, client, defaultIterations, 0));
        }

        if (geminiEnabled) {
            order.add(new ModelRunConfig("Gemini",
                    routingService.getClient(LlmProvider.GEMINI), geminiIterations, geminiDelayMs));
        }
        if (groqEnabled) {
            order.add(new ModelRunConfig("Groq",
                    routingService.getClient(LlmProvider.GROQ), groqIterations, groqDelayMs));
        }

        return order;
    }

    private ChatClient createOllamaClient(String model) {
        log.info("Creating Ollama ChatClient for model: {}", model);
        var api = new OllamaApi.Builder().baseUrl(ollamaBaseUrl).build();
        var chatModel = OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(model).temperature(0.0).build())
                .build();
        return ChatClient.create(chatModel);
    }

    private record ModelRunConfig(String label, ChatClient client, int iterations, long delayMs) {
        boolean isTraditional() { return client == null; }
    }

    private record ExpectedOutcome(String expectedDate, Boolean expectedSuccess) {}

    // ---------------------------------------------------------------
    //  Orchestration
    // ---------------------------------------------------------------

    private void runModelBenchmark(ModelRunConfig cfg, BenchmarkCsvExporter csv,
                                   Map<String, Integer> completed,
                                   List<BenchmarkResult> sessionResults,
                                   TestDataPool dataPool) throws java.io.IOException {

        boolean allDone = true;
        for (String scenario : ALL_SCENARIOS) {
            if (completed.getOrDefault(cfg.label + "," + scenario, 0) < cfg.iterations) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            log.info("--- {} : SKIPPED (already complete) ---", cfg.label);
            return;
        }

        if (!cfg.isTraditional() && !pingOllama()) {
            log.error("--- {} : Ollama not responding, SKIPPING ---", cfg.label);
            return;
        }

        int startFrom = Integer.MAX_VALUE;
        for (String scenario : ALL_SCENARIOS) {
            startFrom = Math.min(startFrom,
                    completed.getOrDefault(cfg.label + "," + scenario, 0));
        }
        if (startFrom == Integer.MAX_VALUE) startFrom = 0;

        log.info("--- {} : running {} iterations (from {}) ---",
                cfg.label, cfg.iterations, startFrom + 1);

        int failRetrieval = 0, failNorm = 0, failCmd = 0;
        boolean skipRetrieval = false, skipNorm = false, skipCmd = false;

        for (int i = startFrom; i < cfg.iterations; i++) {
            String email = dataPool.randomEmail();
            NormalizationTestCase normCase = dataPool.randomNormCase();
            MeetingTestCase meetingCase = dataPool.randomMeetingCase();

            if (!skipRetrieval) {
                log.info("  [{}] iter {} DataRetrieval ({})", cfg.label, i + 1, email);
                BenchmarkResult r = measureRetrieval(cfg, email);
                log.info("  [{}] iter {} DataRetrieval -> {} {}ms",
                        cfg.label, i + 1, r.accuracy() ? "OK" : "FAIL", f(r.latencyMs()));
                csv.writeResult(r);
                sessionResults.add(r);
                failRetrieval = r.accuracy() ? 0 : failRetrieval + 1;
                if (failRetrieval >= EARLY_STOP_THRESHOLD) { skipRetrieval = true; log.warn("  {} DataRetrieval early-stopped", cfg.label); }
                rateLimitSleep(cfg.delayMs);
            }

            if (!skipNorm) {
                log.info("  [{}] iter {} DataNorm ({})", cfg.label, i + 1, normCase.request().rawDate());
                BenchmarkResult r = measureNormalization(cfg, normCase);
                log.info("  [{}] iter {} DataNorm -> {} {}ms",
                        cfg.label, i + 1, r.accuracy() ? "OK" : "FAIL", f(r.latencyMs()));
                csv.writeResult(r);
                sessionResults.add(r);
                failNorm = r.accuracy() ? 0 : failNorm + 1;
                if (failNorm >= EARLY_STOP_THRESHOLD) { skipNorm = true; log.warn("  {} DataNorm early-stopped", cfg.label); }
                rateLimitSleep(cfg.delayMs);
            }

            if (!skipCmd) {
                log.info("  [{}] iter {} Command ({})", cfg.label, i + 1, meetingCase.request().title());
                BenchmarkResult r = measureCommand(cfg, meetingCase);
                log.info("  [{}] iter {} Command -> {} {}ms",
                        cfg.label, i + 1, r.accuracy() ? "OK" : "FAIL", f(r.latencyMs()));
                csv.writeResult(r);
                sessionResults.add(r);
                failCmd = r.accuracy() ? 0 : failCmd + 1;
                if (failCmd >= EARLY_STOP_THRESHOLD) { skipCmd = true; log.warn("  {} Command early-stopped", cfg.label); }
                rateLimitSleep(cfg.delayMs);
            }

            if (skipRetrieval && skipNorm && skipCmd) {
                log.warn("  {} all scenarios early-stopped at iter {}", cfg.label, i + 1);
                break;
            }

            if ((i + 1) % 10 == 0) {
                log.info("  {} progress: {}/{}", cfg.label, i + 1, cfg.iterations);
            }
        }
    }

    /**
     * Quick 10-second ping to Ollama /api/tags.
     * If it doesn't respond, the server is down — no point trying LLM calls.
     */
    private boolean pingOllama() {
        try {
            log.info("Pinging Ollama at {} ...", ollamaBaseUrl);
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() == 200;
            log.info("Ollama ping: {} (status {})", ok ? "OK" : "FAIL", resp.statusCode());
            return ok;
        } catch (Exception e) {
            log.error("Ollama ping failed: {}", e.getMessage());
            return false;
        }
    }

    private void rateLimitSleep(long delayMs) {
        if (delayMs > 0) {
            try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private static String f(double ms) { return String.format("%.0f", ms); }

    // ---------------------------------------------------------------
    //  Measurements
    // ---------------------------------------------------------------

    private BenchmarkResult measureRetrieval(ModelRunConfig cfg, String email) {
        long start = System.nanoTime();
        try {
            if (cfg.isTraditional()) {
                UserProfileResult result = traditionalRetrieval.fetchUserByEmail(email);
                double ms = ns2ms(System.nanoTime() - start);
                validator.validate(result);
                return ok(cfg.label, SCENARIO_RETRIEVAL, ms);
            }
            return llmCall(cfg.label, SCENARIO_RETRIEVAL, cfg.client,
                    retrievalPrompt(email),
                    new Object[]{new UserProfileQueryTool(userProfileRepo)},
                    UserProfileResult.class, start, null);
        } catch (Exception e) {
            return fail(cfg.label, SCENARIO_RETRIEVAL, ns2ms(System.nanoTime() - start), e);
        }
    }

    private BenchmarkResult measureNormalization(ModelRunConfig cfg, NormalizationTestCase normCase) {
        long start = System.nanoTime();
        try {
            if (cfg.isTraditional()) {
                NormalizedDataResult result = traditionalNormalization.normalize(normCase.request());
                double ms = ns2ms(System.nanoTime() - start);
                validator.validate(result);
                if (!result.normalizedDate().equals(normCase.expectedDate())) {
                    throw new ParameterMismatchException(
                            "expected " + normCase.expectedDate() + " got " + result.normalizedDate());
                }
                return ok(cfg.label, SCENARIO_NORMALIZATION, ms);
            }
            return llmCall(cfg.label, SCENARIO_NORMALIZATION, cfg.client,
                    normalizationPrompt(normCase.request()), null,
                    NormalizedDataResult.class, start,
                    new ExpectedOutcome(normCase.expectedDate(), null));
        } catch (Exception e) {
            return fail(cfg.label, SCENARIO_NORMALIZATION, ns2ms(System.nanoTime() - start), e);
        }
    }

    private BenchmarkResult measureCommand(ModelRunConfig cfg, MeetingTestCase meetingCase) {
        long start = System.nanoTime();
        try {
            if (cfg.isTraditional()) {
                MeetingBookingResult result = traditionalCommand.bookMeeting(meetingCase.request());
                double ms = ns2ms(System.nanoTime() - start);
                validator.validate(result);
                return ok(cfg.label, SCENARIO_COMMAND, ms);
            }
            return llmCall(cfg.label, SCENARIO_COMMAND, cfg.client,
                    commandPrompt(meetingCase.request()),
                    new Object[]{new MeetingBookingTool(meetingRepo)},
                    MeetingBookingResult.class, start, null);
        } catch (Exception e) {
            return fail(cfg.label, SCENARIO_COMMAND, ns2ms(System.nanoTime() - start), e);
        }
    }

    // ---------------------------------------------------------------
    //  LLM call with timeout
    // ---------------------------------------------------------------

    private <T> BenchmarkResult llmCall(String modelLabel, String scenario,
                                        ChatClient client, String userPrompt,
                                        Object[] tools, Class<T> responseType,
                                        long startNanos, ExpectedOutcome expected) throws Exception {

        ChatClient.ChatClientRequestSpec spec = client.prompt()
                .system(JSON_SYSTEM).user(userPrompt);
        if (tools != null) spec = spec.tools(tools);

        final var finalSpec = spec;
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "llm-call");
            t.setDaemon(true);
            return t;
        });

        ChatResponse response;
        try {
            Future<ChatResponse> future = exec.submit(() -> finalSpec.call().chatResponse());
            try {
                response = future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                future.cancel(true);
                log.warn("[{}] {} TIMEOUT after {}s", modelLabel, scenario, CALL_TIMEOUT_SECONDS);
                return new BenchmarkResult(modelLabel, scenario, false,
                        ns2ms(System.nanoTime() - startNanos), -1, 0, 0, "TimeoutException");
            } catch (java.util.concurrent.ExecutionException ee) {
                Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                throw (cause instanceof Exception ex) ? ex : new RuntimeException(cause);
            }
        } finally {
            exec.shutdownNow();
        }

        double latencyMs = ns2ms(System.nanoTime() - startNanos);
        long[] tokens = extractTokens(response);
        String content = extractContent(response);

        try {
            T entity = objectMapper.readValue(content, responseType);
            validateEntity(scenario, entity);

            if (expected != null && expected.expectedDate() != null
                    && entity instanceof NormalizedDataResult nr) {
                if (!nr.normalizedDate().equals(expected.expectedDate())) {
                    throw new ParameterMismatchException(
                            "expected " + expected.expectedDate() + " got " + nr.normalizedDate());
                }
            }
        } catch (Exception e) {
            log.warn("[{}] {} — {}: {}\n  Raw: {}",
                    modelLabel, scenario, e.getClass().getSimpleName(), e.getMessage(),
                    content.length() > 300 ? content.substring(0, 300) + "..." : content);
            return new BenchmarkResult(modelLabel, scenario, false, latencyMs, -1,
                    tokens[0], tokens[1], e.getClass().getSimpleName());
        }

        return new BenchmarkResult(modelLabel, scenario, true, latencyMs, -1,
                tokens[0], tokens[1], "");
    }

    // ---------------------------------------------------------------
    //  Parsing helpers
    // ---------------------------------------------------------------

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            throw new LlmResponseValidationException("Empty ChatResponse");
        }
        String raw = response.getResult().getOutput().getText();
        if (raw == null || raw.isBlank()) {
            throw new LlmResponseValidationException("Blank content");
        }
        return stripMarkdownFences(raw.trim());
    }

    private String stripMarkdownFences(String text) {
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (nl > 0 && end > nl) return text.substring(nl + 1, end).trim();
        }
        int b1 = text.indexOf('{');
        int b2 = text.lastIndexOf('}');
        if (b1 >= 0 && b2 > b1) return text.substring(b1, b2 + 1).trim();
        return text;
    }

    private long[] extractTokens(ChatResponse response) {
        try {
            var usage = response.getMetadata().getUsage();
            return new long[]{
                    usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                    usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0
            };
        } catch (NullPointerException e) {
            return new long[]{0, 0};
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void validateEntity(String scenario, T entity) {
        switch (scenario) {
            case SCENARIO_RETRIEVAL -> validator.validate((UserProfileResult) entity);
            case SCENARIO_NORMALIZATION -> validator.validate((NormalizedDataResult) entity);
            case SCENARIO_COMMAND -> validator.validate((MeetingBookingResult) entity);
        }
    }

    // ---------------------------------------------------------------
    //  Prompts
    // ---------------------------------------------------------------

    private String retrievalPrompt(String email) {
        return String.format(
                "Look up the user profile for email: %s. " +
                "Use the findUserByEmail tool to query the database, then return the result as a JSON object " +
                "with exactly these fields: email, firstName, lastName, phone, address.",
                email);
    }

    private String normalizationPrompt(NormalizationRequest norm) {
        return String.format(
                "Normalize the following data and return a JSON object with exactly two fields: " +
                "\"normalizedDate\" and \"normalizedAddress\".\n\n" +
                "Rules:\n" +
                "- normalizedDate: Convert the date to ISO-8601 format (yyyy-MM-dd).\n" +
                "- normalizedAddress: Capitalize words properly, expand abbreviations " +
                "(st->Street, ave->Avenue, blvd->Boulevard, dr->Drive, ln->Lane, rd->Road, " +
                "apt->Apartment, ste->Suite), and keep state codes as 2-letter uppercase.\n\n" +
                "Input date: %s\nInput address: %s",
                norm.rawDate(), norm.rawAddress());
    }

    private String commandPrompt(MeetingBookingRequest m) {
        return String.format(
                "Book a meeting with these details using the bookMeeting tool, then return its JSON result.\n\n" +
                "Title: %s\nOrganizer: %s\nParticipants: %s\n" +
                "Date: %s\nStart: %s\nEnd: %s\nLocation: %s\n\n" +
                "Return a JSON object with fields: success, meetingId, message.",
                m.title(), m.organizerEmail(), String.join(", ", m.participants()),
                m.date(), m.startTime(), m.endTime(), m.location());
    }

    // ---------------------------------------------------------------
    //  Result builders
    // ---------------------------------------------------------------

    private BenchmarkResult ok(String model, String scenario, double ms) {
        return new BenchmarkResult(model, scenario, true, ms, -1, 0, 0, "");
    }

    private BenchmarkResult fail(String model, String scenario, double ms, Exception e) {
        return new BenchmarkResult(model, scenario, false, ms, -1, 0, 0,
                e.getClass().getSimpleName());
    }

    private static double ns2ms(long nanos) { return nanos / 1_000_000.0; }

    // ---------------------------------------------------------------
    //  Summary
    // ---------------------------------------------------------------

    private void logSummary(List<BenchmarkResult> results) {
        if (results.isEmpty()) { log.info("No results this session"); return; }
        log.info("--- Summary ---");
        results.stream()
                .collect(Collectors.groupingBy(r -> r.model() + " / " + r.scenario()))
                .entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    var g = e.getValue();
                    double avgMs = g.stream().mapToDouble(BenchmarkResult::latencyMs).average().orElse(0);
                    long acc = g.stream().filter(BenchmarkResult::accuracy).count();
                    log.info("  {} — {}ms avg | {}/{} accuracy",
                            e.getKey(), String.format("%.1f", avgMs), acc, g.size());
                });
    }
}
