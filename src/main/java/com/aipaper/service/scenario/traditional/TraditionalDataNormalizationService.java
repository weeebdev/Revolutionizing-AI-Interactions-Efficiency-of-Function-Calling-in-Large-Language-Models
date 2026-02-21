package com.aipaper.service.scenario.traditional;

import com.aipaper.dto.NormalizationRequest;
import com.aipaper.dto.NormalizedDataResult;
import com.aipaper.service.scenario.DataNormalizationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TraditionalDataNormalizationService implements DataNormalizationService {

    private static DateTimeFormatter ci(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH);
    }

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                          // 2024-01-05
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),                 // 01/05/2024
            DateTimeFormatter.ofPattern("M/d/yyyy"),                   // 8/5/2024
            DateTimeFormatter.ofPattern("M/d/yy"),                     // 8/15/23
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),                 // 01-05-2024
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),                 // 2024.01.05
            ci("MMMM d, yyyy"),                                        // January 5, 2024
            ci("MMMM d yyyy"),                                         // March 15 2023
            ci("MMM d, yyyy"),                                         // Jan 5, 2024
            ci("MMM d yyyy"),                                          // Feb 14 2024
            ci("MMM dd yyyy"),                                         // Nov 11 2023
            ci("d MMMM yyyy"),                                         // 5 January 2024
            ci("d MMM yyyy"),                                          // 5 Jan 2024
            ci("d-MMM-yyyy"),                                          // 6-Jan-2024
            ci("d MMMM ''yy"),                                         // 15 June '23
            ci("MMM yyyy d"),                                          // Oct 2023 7
            DateTimeFormatter.ofPattern("MM.dd.yyyy"),                 // 01.05.2024
            ci("MMMM dd, yyyy"),                                       // January 05, 2024
            ci("MMM dd, yyyy")                                         // Jan 05, 2024
    );

    private static final Map<String, String> WORD_NUMBERS = Map.ofEntries(
            Map.entry("first", "1"), Map.entry("second", "2"), Map.entry("third", "3"),
            Map.entry("fourth", "4"), Map.entry("fifth", "5"), Map.entry("sixth", "6"),
            Map.entry("seventh", "7"), Map.entry("eighth", "8"), Map.entry("ninth", "9"),
            Map.entry("tenth", "10"), Map.entry("eleventh", "11"), Map.entry("twelfth", "12"),
            Map.entry("thirteenth", "13"), Map.entry("fourteenth", "14"), Map.entry("fifteenth", "15"),
            Map.entry("sixteenth", "16"), Map.entry("seventeenth", "17"), Map.entry("eighteenth", "18"),
            Map.entry("nineteenth", "19"), Map.entry("twentieth", "20"),
            Map.entry("twenty-first", "21"), Map.entry("twenty-second", "22"), Map.entry("twenty-third", "23"),
            Map.entry("twenty-fourth", "24"), Map.entry("twenty-fifth", "25"), Map.entry("twenty-sixth", "26"),
            Map.entry("twenty-seventh", "27"), Map.entry("twenty-eighth", "28"), Map.entry("twenty-ninth", "29"),
            Map.entry("thirtieth", "30"), Map.entry("thirty-first", "31"),
            Map.entry("fifteen", "15"), Map.entry("sixteen", "16"), Map.entry("seventeen", "17"),
            Map.entry("eighteen", "18"), Map.entry("nineteen", "19"), Map.entry("twenty", "20")
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
                .replaceAll("(?i)(st|nd|rd|th)(?=,|\\s|$)", "")
                .replaceAll("(?i)\\bthe\\s+", "")
                .replaceAll("(?i)\\s+of\\s+", " ");

        // Replace word-numbers (e.g., "fifteenth" -> "15", "twenty-second" -> "22")
        for (var entry : WORD_NUMBERS.entrySet()) {
            String pattern = "(?i)\\b" + Pattern.quote(entry.getKey()) + "\\b";
            cleaned = cleaned.replaceAll(pattern, entry.getValue());
        }

        cleaned = cleaned.replaceAll("\\s+", " ").trim();

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
