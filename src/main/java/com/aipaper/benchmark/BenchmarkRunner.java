package com.aipaper.benchmark;

import com.aipaper.dto.MeetingBookingRequest;
import com.aipaper.dto.MeetingBookingResult;
import com.aipaper.dto.NormalizationRequest;
import com.aipaper.dto.NormalizedDataResult;
import com.aipaper.dto.UserProfileResult;
import com.aipaper.exception.LlmResponseValidationException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("benchmark")
public class BenchmarkRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private static final String SCENARIO_RETRIEVAL = "DataRetrieval";
    private static final String SCENARIO_NORMALIZATION = "DataNormalization";
    private static final String SCENARIO_COMMAND = "CommandExecution";
    private static final String MODEL_TRADITIONAL = "Traditional";

    private static final String TEST_EMAIL = "alice.johnson@example.com";

    private static final NormalizationRequest TEST_NORM =
            new NormalizationRequest("Jan 5th, 2024", "123 main st, apt 4, new york, ny 10001");

    private static final MeetingBookingRequest TEST_MEETING = new MeetingBookingRequest(
            "Weekly Standup",
            "alice.johnson@example.com",
            List.of("bob.smith@example.com", "carol.williams@example.com"),
            "2024-06-15", "09:00", "09:30", "Conference Room A");

    private static final String JSON_SYSTEM =
            "You are a precise data API. Always respond with ONLY a raw JSON object. " +
            "Never include markdown formatting, code fences, or explanations.";

    @Value("${benchmark.iterations:1000}")
    private int iterations;

    @Value("${benchmark.warmup-iterations:50}")
    private int warmupIterations;

    @Value("${benchmark.output-file:benchmark_results.csv}")
    private String outputFile;

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
        log.info("========================================");
        log.info("  BENCHMARK STARTING");
        log.info("  Iterations: {}, Warmup: {}", iterations, warmupIterations);
        log.info("  Output: {}", outputFile);
        log.info("========================================");

        List<BenchmarkResult> allResults = new ArrayList<>(iterations * 4 * 3);

        runModelBenchmark(MODEL_TRADITIONAL, null, allResults);

        for (LlmProvider provider : LlmProvider.values()) {
            runModelBenchmark(provider.name(), provider, allResults);
        }

        BenchmarkCsvExporter.export(allResults, outputFile);
        logSummary(allResults);

        log.info("========================================");
        log.info("  BENCHMARK COMPLETE — {} results", allResults.size());
        log.info("========================================");
    }

    // ---------------------------------------------------------------
    //  Orchestration
    // ---------------------------------------------------------------

    private void runModelBenchmark(String modelName, LlmProvider provider,
                                   List<BenchmarkResult> results) {
        boolean isTraditional = provider == null;

        log.info("--- {} : warmup ({} iterations) ---", modelName, warmupIterations);
        for (int i = 0; i < warmupIterations; i++) {
            quietRun(() -> doRetrieval(isTraditional, provider));
            quietRun(() -> doNormalization(isTraditional, provider));
            quietRun(() -> doCommand(isTraditional, provider));
        }

        log.info("--- {} : benchmark ({} iterations) ---", modelName, iterations);
        for (int i = 0; i < iterations; i++) {
            results.add(measureRetrieval(modelName, isTraditional, provider));
            results.add(measureNormalization(modelName, isTraditional, provider));
            results.add(measureCommand(modelName, isTraditional, provider));

            if ((i + 1) % 100 == 0) {
                log.info("  {} progress: {}/{}", modelName, i + 1, iterations);
            }
        }
    }

    // ---------------------------------------------------------------
    //  Warmup helpers (swallow exceptions)
    // ---------------------------------------------------------------

    private void quietRun(Runnable task) {
        try { task.run(); } catch (Exception ignored) {}
    }

    private void doRetrieval(boolean isTraditional, LlmProvider provider) {
        if (isTraditional) {
            traditionalRetrieval.fetchUserByEmail(TEST_EMAIL);
        } else {
            routingService.getClient(provider).prompt()
                    .system(JSON_SYSTEM).user(retrievalPrompt())
                    .tools(new UserProfileQueryTool(userProfileRepo))
                    .call().content();
        }
    }

    private void doNormalization(boolean isTraditional, LlmProvider provider) {
        if (isTraditional) {
            traditionalNormalization.normalize(TEST_NORM);
        } else {
            routingService.getClient(provider).prompt()
                    .system(JSON_SYSTEM).user(normalizationPrompt())
                    .call().content();
        }
    }

    private void doCommand(boolean isTraditional, LlmProvider provider) {
        if (isTraditional) {
            traditionalCommand.bookMeeting(TEST_MEETING);
        } else {
            routingService.getClient(provider).prompt()
                    .system(JSON_SYSTEM).user(commandPrompt())
                    .tools(new MeetingBookingTool(meetingRepo))
                    .call().content();
        }
    }

    // ---------------------------------------------------------------
    //  Measurement: Data Retrieval
    // ---------------------------------------------------------------

    private BenchmarkResult measureRetrieval(String modelName, boolean isTraditional,
                                             LlmProvider provider) {
        long start = System.nanoTime();
        try {
            if (isTraditional) {
                UserProfileResult result = traditionalRetrieval.fetchUserByEmail(TEST_EMAIL);
                double ms = nanosToMs(System.nanoTime() - start);
                validator.validate(result);
                return ok(modelName, SCENARIO_RETRIEVAL, ms);
            } else {
                return llmCallWithMetrics(modelName, SCENARIO_RETRIEVAL, provider,
                        retrievalPrompt(),
                        new Object[]{new UserProfileQueryTool(userProfileRepo)},
                        UserProfileResult.class, start);
            }
        } catch (Exception e) {
            return fail(modelName, SCENARIO_RETRIEVAL, nanosToMs(System.nanoTime() - start), e);
        }
    }

    // ---------------------------------------------------------------
    //  Measurement: Data Normalization
    // ---------------------------------------------------------------

    private BenchmarkResult measureNormalization(String modelName, boolean isTraditional,
                                                 LlmProvider provider) {
        long start = System.nanoTime();
        try {
            if (isTraditional) {
                NormalizedDataResult result = traditionalNormalization.normalize(TEST_NORM);
                double ms = nanosToMs(System.nanoTime() - start);
                validator.validate(result);
                return ok(modelName, SCENARIO_NORMALIZATION, ms);
            } else {
                return llmCallWithMetrics(modelName, SCENARIO_NORMALIZATION, provider,
                        normalizationPrompt(),
                        null,
                        NormalizedDataResult.class, start);
            }
        } catch (Exception e) {
            return fail(modelName, SCENARIO_NORMALIZATION, nanosToMs(System.nanoTime() - start), e);
        }
    }

    // ---------------------------------------------------------------
    //  Measurement: Command Execution
    // ---------------------------------------------------------------

    private BenchmarkResult measureCommand(String modelName, boolean isTraditional,
                                           LlmProvider provider) {
        long start = System.nanoTime();
        try {
            if (isTraditional) {
                MeetingBookingResult result = traditionalCommand.bookMeeting(TEST_MEETING);
                double ms = nanosToMs(System.nanoTime() - start);
                validator.validate(result);
                return ok(modelName, SCENARIO_COMMAND, ms);
            } else {
                return llmCallWithMetrics(modelName, SCENARIO_COMMAND, provider,
                        commandPrompt(),
                        new Object[]{new MeetingBookingTool(meetingRepo)},
                        MeetingBookingResult.class, start);
            }
        } catch (Exception e) {
            return fail(modelName, SCENARIO_COMMAND, nanosToMs(System.nanoTime() - start), e);
        }
    }

    // ---------------------------------------------------------------
    //  Unified LLM call — captures latency, accuracy, token usage
    // ---------------------------------------------------------------

    private <T> BenchmarkResult llmCallWithMetrics(String modelName, String scenario,
                                                   LlmProvider provider, String userPrompt,
                                                   Object[] tools, Class<T> responseType,
                                                   long startNanos) throws Exception {
        ChatClient client = routingService.getClient(provider);

        ChatClient.ChatClientRequestSpec spec = client.prompt()
                .system(JSON_SYSTEM)
                .user(userPrompt);

        if (tools != null) {
            spec = spec.tools(tools);
        }

        ChatResponse response = spec.call().chatResponse();
        double latencyMs = nanosToMs(System.nanoTime() - startNanos);

        String content = extractContent(response);
        T entity = objectMapper.readValue(content, responseType);
        validateEntity(scenario, entity);

        long[] tokens = extractTokens(response);

        return new BenchmarkResult(modelName, scenario, true, latencyMs, -1,
                tokens[0], tokens[1], "");
    }

    // ---------------------------------------------------------------
    //  Response parsing helpers
    // ---------------------------------------------------------------

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            throw new LlmResponseValidationException("Empty ChatResponse from LLM");
        }
        String raw = response.getResult().getOutput().getText();
        if (raw == null || raw.isBlank()) {
            throw new LlmResponseValidationException("Blank content from LLM");
        }
        return stripMarkdownFences(raw.trim());
    }

    private String stripMarkdownFences(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }

    private long[] extractTokens(ChatResponse response) {
        try {
            var usage = response.getMetadata().getUsage();
            long prompt = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            long completion = usage.getGenerationTokens() != null ? usage.getGenerationTokens() : 0;
            return new long[]{prompt, completion};
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
    //  Prompt construction
    // ---------------------------------------------------------------

    private String retrievalPrompt() {
        return String.format(
                "Look up the user profile for email: %s. " +
                "Use the findUserByEmail tool to query the database, then return the result as a JSON object " +
                "with exactly these fields: email, firstName, lastName, phone, address.",
                TEST_EMAIL);
    }

    private String normalizationPrompt() {
        return String.format(
                "Normalize the following data and return a JSON object with exactly two fields: " +
                "\"normalizedDate\" and \"normalizedAddress\".\n\n" +
                "Rules:\n" +
                "- normalizedDate: Convert the date to ISO-8601 format (yyyy-MM-dd).\n" +
                "- normalizedAddress: Capitalize words properly, expand abbreviations " +
                "(st->Street, ave->Avenue, blvd->Boulevard, dr->Drive, ln->Lane, rd->Road, " +
                "apt->Apartment, ste->Suite), and keep state codes as 2-letter uppercase.\n\n" +
                "Input date: %s\n" +
                "Input address: %s",
                TEST_NORM.rawDate(), TEST_NORM.rawAddress());
    }

    private String commandPrompt() {
        String participants = String.join(", ", TEST_MEETING.participants());
        return String.format(
                "Book a meeting with these details. Validate all parameters before calling the bookMeeting tool.\n\n" +
                "Title: %s\n" +
                "Organizer email: %s\n" +
                "Participants: %s\n" +
                "Date: %s\n" +
                "Start time: %s\n" +
                "End time: %s\n" +
                "Location: %s\n\n" +
                "If parameters are valid, use the bookMeeting tool and return its JSON result. " +
                "If invalid, return {\"success\":false,\"meetingId\":null,\"message\":\"<reason>\"}. " +
                "Return a JSON object with fields: success, meetingId, message.",
                TEST_MEETING.title(), TEST_MEETING.organizerEmail(), participants,
                TEST_MEETING.date(), TEST_MEETING.startTime(), TEST_MEETING.endTime(),
                TEST_MEETING.location());
    }

    // ---------------------------------------------------------------
    //  Result builders
    // ---------------------------------------------------------------

    private BenchmarkResult ok(String model, String scenario, double latencyMs) {
        return new BenchmarkResult(model, scenario, true, latencyMs, -1, 0, 0, "");
    }

    private BenchmarkResult fail(String model, String scenario, double latencyMs, Exception e) {
        return new BenchmarkResult(model, scenario, false, latencyMs, -1, 0, 0,
                e.getClass().getSimpleName());
    }

    private static double nanosToMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    // ---------------------------------------------------------------
    //  Summary logging
    // ---------------------------------------------------------------

    private void logSummary(List<BenchmarkResult> results) {
        log.info("--- Summary ---");
        Map<String, List<BenchmarkResult>> grouped = results.stream()
                .collect(Collectors.groupingBy(r -> r.model() + " / " + r.scenario()));

        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<BenchmarkResult> group = entry.getValue();
                    double avgLatency = group.stream()
                            .mapToDouble(BenchmarkResult::latencyMs).average().orElse(0);
                    long accurate = group.stream().filter(BenchmarkResult::accuracy).count();
                    double avgPrompt = group.stream()
                            .mapToLong(BenchmarkResult::promptTokens).average().orElse(0);
                    double avgCompletion = group.stream()
                            .mapToLong(BenchmarkResult::completionTokens).average().orElse(0);

                    log.info("  {} — avg latency: {}ms | accuracy: {}/{} | avg tokens: {} prompt / {} completion",
                            entry.getKey(),
                            String.format("%.2f", avgLatency),
                            accurate, group.size(),
                            String.format("%.0f", avgPrompt),
                            String.format("%.0f", avgCompletion));
                });
    }
}
