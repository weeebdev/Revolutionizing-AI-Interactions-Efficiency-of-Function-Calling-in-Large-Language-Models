package com.aipaper.service.scenario.traditional;

import com.aipaper.dto.NormalizationRequest;
import com.aipaper.dto.NormalizedDataResult;
import com.aipaper.service.scenario.DataNormalizationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TraditionalDataNormalizationService implements DataNormalizationService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                          // 2024-01-05
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),                 // 01/05/2024
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),                 // 01-05-2024
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),                 // 2024.01.05
            DateTimeFormatter.ofPattern("MMMM d, yyyy"),               // January 5, 2024
            DateTimeFormatter.ofPattern("MMM d, yyyy"),                // Jan 5, 2024
            DateTimeFormatter.ofPattern("d MMMM yyyy"),                // 5 January 2024
            DateTimeFormatter.ofPattern("d MMM yyyy"),                 // 5 Jan 2024
            DateTimeFormatter.ofPattern("MM.dd.yyyy"),                 // 01.05.2024
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),                 // 05/01/2024 (ambiguous, tried last)
            DateTimeFormatter.ofPattern("MMMM dd, yyyy"),              // January 05, 2024
            DateTimeFormatter.ofPattern("MMM dd, yyyy")                // Jan 05, 2024
    );

    private static final Map<String, String> ADDRESS_ABBREVIATIONS = Map.ofEntries(
            Map.entry("st", "Street"),
            Map.entry("ave", "Avenue"),
            Map.entry("blvd", "Boulevard"),
            Map.entry("dr", "Drive"),
            Map.entry("ln", "Lane"),
            Map.entry("ct", "Court"),
            Map.entry("rd", "Road"),
            Map.entry("apt", "Apartment"),
            Map.entry("ste", "Suite"),
            Map.entry("pl", "Place"),
            Map.entry("cir", "Circle"),
            Map.entry("pkwy", "Parkway"),
            Map.entry("hwy", "Highway"),
            Map.entry("fl", "Floor")
    );

    private static final Pattern STATE_ABBREV_PATTERN =
            Pattern.compile("\\b([A-Za-z]{2})\\s+(\\d{5}(-\\d{4})?)$");

    @Override
    public NormalizedDataResult normalize(NormalizationRequest request) {
        String normalizedDate = normalizeDate(request.rawDate());
        String normalizedAddress = normalizeAddress(request.rawAddress());
        return new NormalizedDataResult(normalizedDate, normalizedAddress);
    }

    private String normalizeDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Date input is empty");
        }

        String cleaned = raw.trim()
                .replaceAll("(?i)(st|nd|rd|th)(?=,|\\s)", "");

        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(cleaned, fmt);
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new IllegalArgumentException("Unable to parse date: " + raw);
    }

    private String normalizeAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Address input is empty");
        }

        String[] parts = raw.split(",");
        StringBuilder normalized = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;

            String processed = expandAbbreviations(titleCase(part));

            Matcher stateMatcher = STATE_ABBREV_PATTERN.matcher(processed);
            if (stateMatcher.find()) {
                String before = processed.substring(0, stateMatcher.start()).trim();
                String stateCode = stateMatcher.group(1).toUpperCase();
                String zip = stateMatcher.group(2);
                processed = before + (before.isEmpty() ? "" : " ") + stateCode + " " + zip;
            }

            if (!normalized.isEmpty()) {
                normalized.append(", ");
            }
            normalized.append(processed);
        }

        return normalized.toString();
    }

    private String titleCase(String input) {
        String[] words = input.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1));
                }
            }
        }
        return sb.toString();
    }

    private String expandAbbreviations(String input) {
        String[] words = input.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            String lower = word.toLowerCase().replaceAll("[.,]$", "");
            String suffix = word.substring(lower.length());
            String expanded = ADDRESS_ABBREVIATIONS.getOrDefault(lower, word);
            sb.append(expanded).append(suffix);
        }
        return sb.toString();
    }
}
