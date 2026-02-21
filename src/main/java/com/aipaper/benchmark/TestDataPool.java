package com.aipaper.benchmark;

import com.aipaper.dto.MeetingBookingRequest;
import com.aipaper.dto.NormalizationRequest;

import java.util.List;
import java.util.Random;

public final class TestDataPool {

    private final Random rng;

    private static final List<String> EMAILS = List.of(
            "alice.johnson@example.com",
            "bob.smith@example.com",
            "carol.williams@example.com",
            "david.brown@example.com",
            "eve.davis@example.com"
    );

    public record NormalizationTestCase(NormalizationRequest request, String expectedDate) {}

    private static final List<NormalizationTestCase> NORM_CASES = List.of(
            new NormalizationTestCase(
                    new NormalizationRequest("2024-01-05", "123 main st, apt 4, new york, ny 10001"),
                    "2024-01-05"),
            new NormalizationTestCase(
                    new NormalizationRequest("12/25/2022", "789 pine blvd, chicago, il 60601"),
                    "2022-12-25"),
            new NormalizationTestCase(
                    new NormalizationRequest("01/15/2024", "350 fifth ave, new york, ny 10118"),
                    "2024-01-15"),
            new NormalizationTestCase(
                    new NormalizationRequest("03-22-2023", "1 infinite loop, cupertino, ca 95014"),
                    "2023-03-22"),
            new NormalizationTestCase(
                    new NormalizationRequest("March 15 2023", "1600 pennsylvania ave, washington, dc 20500"),
                    "2023-03-15"),
            new NormalizationTestCase(
                    new NormalizationRequest("Jan 5th, 2024", "456 oak ave, ste 200, los angeles, ca 90001"),
                    "2024-01-05"),
            new NormalizationTestCase(
                    new NormalizationRequest("Nov 11th 2023", "77 massachusetts ave, cambridge, ma 02139"),
                    "2023-11-11"),
            new NormalizationTestCase(
                    new NormalizationRequest("2024-02-29", "42 elm dr, san francisco, ca 94102"),
                    "2024-02-29")
    );

    public record MeetingTestCase(MeetingBookingRequest request, boolean expectedSuccess) {}

    private static final List<MeetingTestCase> MEETING_CASES = List.of(
            new MeetingTestCase(new MeetingBookingRequest("Weekly Standup", "alice.johnson@example.com",
                    List.of("bob.smith@example.com", "carol.williams@example.com"),
                    "2024-06-15", "09:00", "09:30", "Conference Room A"), true),
            new MeetingTestCase(new MeetingBookingRequest("Sprint Planning", "bob.smith@example.com",
                    List.of("alice.johnson@example.com", "david.brown@example.com"),
                    "2024-06-17", "10:00", "11:00", "Board Room B"), true),
            new MeetingTestCase(new MeetingBookingRequest("Design Review", "carol.williams@example.com",
                    List.of("eve.davis@example.com"),
                    "2024-06-18", "14:00", "15:30", "Room 301"), true),
            new MeetingTestCase(new MeetingBookingRequest("1:1 Check-in", "david.brown@example.com",
                    List.of("alice.johnson@example.com"),
                    "2024-06-19", "11:00", "11:30", "Office 204"), true),
            new MeetingTestCase(new MeetingBookingRequest("Team Retrospective", "eve.davis@example.com",
                    List.of("bob.smith@example.com", "carol.williams@example.com", "david.brown@example.com"),
                    "2024-06-20", "15:00", "16:00", "Main Hall"), true)
    );

    public TestDataPool(long seed) {
        this.rng = new Random(seed);
    }

    public String randomEmail() {
        return EMAILS.get(rng.nextInt(EMAILS.size()));
    }

    public NormalizationTestCase randomNormCase() {
        return NORM_CASES.get(rng.nextInt(NORM_CASES.size()));
    }

    public MeetingTestCase randomMeetingCase() {
        return MEETING_CASES.get(rng.nextInt(MEETING_CASES.size()));
    }
}
