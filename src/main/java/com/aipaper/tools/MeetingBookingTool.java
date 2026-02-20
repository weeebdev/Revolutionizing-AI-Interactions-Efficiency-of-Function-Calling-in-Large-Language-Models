package com.aipaper.tools;

import com.aipaper.entity.Meeting;
import com.aipaper.repository.MeetingRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class MeetingBookingTool {

    private final MeetingRepository repository;

    public MeetingBookingTool(MeetingRepository repository) {
        this.repository = repository;
    }

    @Tool(description = "Book a new meeting by inserting it into the database. " +
            "All parameters must be validated before calling. " +
            "Returns a JSON object with success status and the meeting ID.")
    public String bookMeeting(
            @ToolParam(description = "Meeting title") String title,
            @ToolParam(description = "Organizer's email address") String organizerEmail,
            @ToolParam(description = "Comma-separated list of participant email addresses") String participants,
            @ToolParam(description = "Meeting date in yyyy-MM-dd format") String date,
            @ToolParam(description = "Start time in HH:mm format") String startTime,
            @ToolParam(description = "End time in HH:mm format") String endTime,
            @ToolParam(description = "Meeting location or room name") String location) {

        try {
            LocalDate meetingDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalTime start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"));

            if (!end.isAfter(start)) {
                return "{\"success\":false,\"meetingId\":null,\"message\":\"End time must be after start time\"}";
            }

            Meeting meeting = new Meeting(title, organizerEmail, participants, meetingDate, start, end, location);
            Meeting saved = repository.save(meeting);

            return String.format(
                    "{\"success\":true,\"meetingId\":%d,\"message\":\"Meeting booked successfully\"}",
                    saved.getId());

        } catch (DateTimeParseException e) {
            return "{\"success\":false,\"meetingId\":null,\"message\":\"Invalid date/time format: " +
                    e.getMessage().replace("\"", "'") + "\"}";
        } catch (Exception e) {
            return "{\"success\":false,\"meetingId\":null,\"message\":\"Booking failed: " +
                    e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
