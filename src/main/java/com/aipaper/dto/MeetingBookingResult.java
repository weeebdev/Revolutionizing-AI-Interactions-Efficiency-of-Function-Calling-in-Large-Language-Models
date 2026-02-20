package com.aipaper.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MeetingBookingResult(
        @JsonProperty(required = true, value = "success") boolean success,
        @JsonProperty(value = "meetingId") Long meetingId,
        @JsonProperty(value = "message") String message
) {}
