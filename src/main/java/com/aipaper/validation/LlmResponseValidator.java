package com.aipaper.validation;

import com.aipaper.dto.MeetingBookingResult;
import com.aipaper.dto.NormalizedDataResult;
import com.aipaper.dto.UserProfileResult;
import com.aipaper.exception.ParameterMismatchException;
import com.aipaper.exception.SchemaValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Component
public class LlmResponseValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern ISO_DATE_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    public void validate(UserProfileResult result) {
        if (result == null) {
            throw new SchemaValidationException("LLM returned null UserProfileResult");
        }
        requireField("email", result.email());
        requireField("firstName", result.firstName());
        requireField("lastName", result.lastName());

        if (!EMAIL_PATTERN.matcher(result.email()).matches()) {
            throw new ParameterMismatchException(
                    "Invalid email format in LLM response: " + result.email());
        }
    }

    public void validate(NormalizedDataResult result) {
        if (result == null) {
            throw new SchemaValidationException("LLM returned null NormalizedDataResult");
        }
        requireField("normalizedDate", result.normalizedDate());
        requireField("normalizedAddress", result.normalizedAddress());

        if (!ISO_DATE_PATTERN.matcher(result.normalizedDate()).matches()) {
            throw new ParameterMismatchException(
                    "normalizedDate is not in ISO-8601 format (yyyy-MM-dd): " + result.normalizedDate());
        }

        try {
            LocalDate.parse(result.normalizedDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new ParameterMismatchException(
                    "normalizedDate is not a valid date: " + result.normalizedDate(), e);
        }

        if (result.normalizedAddress().isBlank()) {
            throw new ParameterMismatchException("normalizedAddress is blank");
        }
    }

    public void validate(MeetingBookingResult result) {
        if (result == null) {
            throw new SchemaValidationException("LLM returned null MeetingBookingResult");
        }
        if (result.success() && result.meetingId() == null) {
            throw new ParameterMismatchException(
                    "Successful booking must include a non-null meetingId");
        }
        if (!result.success() && (result.message() == null || result.message().isBlank())) {
            throw new ParameterMismatchException(
                    "Failed booking must include an error message");
        }
    }

    private void requireField(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new SchemaValidationException(
                    "Missing or blank required field: " + fieldName);
        }
    }
}
