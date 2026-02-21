package com.aipaper.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BenchmarkCsvExporter implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkCsvExporter.class);

    static final String HEADER =
            "Model,Scenario,Accuracy,Latency_ms,TTFT_ms,Prompt_Tokens,Completion_Tokens,Error_Type";

    private final BufferedWriter writer;
    private int rowsWritten;

    private BenchmarkCsvExporter(BufferedWriter writer, int existingRows) {
        this.writer = writer;
        this.rowsWritten = existingRows;
    }

    /**
     * Opens the CSV file for incremental writing.
     * If the file already exists and contains data, new rows are appended.
     * If the file is missing or empty, a fresh header is written.
     */
    public static BenchmarkCsvExporter open(String filePath) throws IOException {
        Path path = Path.of(filePath);
        boolean exists = Files.exists(path) && Files.size(path) > 0;

        if (exists) {
            int existingRows = countDataRows(path);
            log.info("Resuming: found {} existing rows in {}", existingRows, filePath);
            BufferedWriter w = new BufferedWriter(new FileWriter(filePath, true));
            return new BenchmarkCsvExporter(w, existingRows);
        }

        BufferedWriter w = new BufferedWriter(new FileWriter(filePath, false));
        w.write(HEADER);
        w.newLine();
        w.flush();
        log.info("Created new benchmark file: {}", filePath);
        return new BenchmarkCsvExporter(w, 0);
    }

    public void writeResult(BenchmarkResult r) throws IOException {
        writer.write(formatRow(r));
        writer.newLine();
        writer.flush();
        rowsWritten++;
    }

    public int getRowsWritten() {
        return rowsWritten;
    }

    @Override
    public void close() throws IOException {
        writer.close();
        log.info("CSV closed — total rows written this session + prior: {}", rowsWritten);
    }

    /**
     * Scans an existing CSV to count how many iterations each model/scenario pair has completed.
     * Returns a map of "Model|Scenario" -> count.
     */
    public static Map<String, Integer> loadCompletedCounts(String filePath) throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        Path path = Path.of(filePath);
        if (!Files.exists(path) || Files.size(path) == 0) {
            return counts;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line = reader.readLine(); // skip header
            if (line == null) return counts;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                int firstComma = trimmed.indexOf(',');
                if (firstComma < 0) continue;
                int secondComma = trimmed.indexOf(',', firstComma + 1);
                if (secondComma < 0) continue;
                String key = trimmed.substring(0, secondComma); // "Model,Scenario"
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static int countDataRows(Path path) throws IOException {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            reader.readLine(); // skip header
            while (reader.readLine() != null) count++;
        }
        return count;
    }

    static String formatRow(BenchmarkResult r) {
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
