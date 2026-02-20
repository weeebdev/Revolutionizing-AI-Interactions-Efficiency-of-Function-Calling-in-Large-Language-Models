package com.aipaper.dto;

import java.util.List;

public record MeetingBookingRequest(
        String title,
        String organizerEmail,
        List<String> participants,
        String date,
        String startTime,
        String endTime,
        String location
) {}
