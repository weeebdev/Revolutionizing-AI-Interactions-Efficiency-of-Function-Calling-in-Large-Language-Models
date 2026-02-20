package com.aipaper.service.scenario.traditional;

import com.aipaper.dto.MeetingBookingRequest;
import com.aipaper.dto.MeetingBookingResult;
import com.aipaper.entity.Meeting;
import com.aipaper.repository.MeetingRepository;
import com.aipaper.service.scenario.CommandExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class TraditionalCommandExecutionService implements CommandExecutionService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final MeetingRepository meetingRepository;

    public TraditionalCommandExecutionService(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    @Override
    @Transactional
    public MeetingBookingResult bookMeeting(MeetingBookingRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.title() == null || request.title().isBlank()) {
            errors.add("Title is required");
        }

        if (request.organizerEmail() == null || !EMAIL_PATTERN.matcher(request.organizerEmail()).matches()) {
            errors.add("Valid organizer email is required");
        }

        if (request.participants() == null || request.participants().isEmpty()) {
            errors.add("At least one participant is required");
        } else {
            for (String p : request.participants()) {
                if (!EMAIL_PATTERN.matcher(p.trim()).matches()) {
                    errors.add("Invalid participant email: " + p);
                }
            }
        }

        LocalDate meetingDate;
        try {
            meetingDate = LocalDate.parse(request.date(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException | NullPointerException e) {
            errors.add("Valid date in yyyy-MM-dd format is required");
            meetingDate = null;
        }

        LocalTime startTime;
        try {
            startTime = LocalTime.parse(request.startTime(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException | NullPointerException e) {
            errors.add("Valid start time in HH:mm format is required");
            startTime = null;
        }

        LocalTime endTime;
        try {
            endTime = LocalTime.parse(request.endTime(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException | NullPointerException e) {
            errors.add("Valid end time in HH:mm format is required");
            endTime = null;
        }

        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            errors.add("End time must be after start time");
        }

        if (!errors.isEmpty()) {
            return new MeetingBookingResult(false, null, String.join("; ", errors));
        }

        String participantsCsv = String.join(",", request.participants());
        Meeting meeting = new Meeting(
                request.title(),
                request.organizerEmail(),
                participantsCsv,
                meetingDate,
                startTime,
                endTime,
                request.location());

        Meeting saved = meetingRepository.save(meeting);
        return new MeetingBookingResult(true, saved.getId(), "Meeting booked successfully");
    }
}
