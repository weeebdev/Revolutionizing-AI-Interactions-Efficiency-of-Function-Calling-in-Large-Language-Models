package com.aipaper.benchmark;

public record BenchmarkResult(
        String model,
        String scenario,
        boolean accuracy,
        double latencyMs,
        double ttftMs,
        long promptTokens,
        long completionTokens,
        String errorType
) {}
