package com.aipaper.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class BenchmarkCsvExporter {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkCsvExporter.class);

    private static final String HEADER =
            "Model,Scenario,Accuracy,Latency_ms,TTFT_ms,Prompt_Tokens,Completion_Tokens,Error_Type";

    private BenchmarkCsvExporter() {}

    public static void export(List<BenchmarkResult> results, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(HEADER);
            writer.newLine();

            for (BenchmarkResult r : results) {
                writer.write(formatRow(r));
                writer.newLine();
            }
        }
        log.info("Exported {} rows to {}", results.size(), filePath);
    }

    private static String formatRow(BenchmarkResult r) {
        return String.format(Locale.US,
                "%s,%s,%s,%.3f,%.3f,%d,%d,%s",
                r.model(),
                r.scenario(),
                r.accuracy(),
                r.latencyMs(),
                r.ttftMs(),
                r.promptTokens(),
                r.completionTokens(),
                r.errorType() != null ? r.errorType() : "");
    }
}
