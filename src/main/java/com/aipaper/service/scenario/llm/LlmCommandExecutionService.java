package com.aipaper.service.scenario.llm;

import com.aipaper.dto.MeetingBookingRequest;
import com.aipaper.dto.MeetingBookingResult;
import com.aipaper.exception.SchemaValidationException;
import com.aipaper.repository.MeetingRepository;
import com.aipaper.service.LlmProvider;
import com.aipaper.service.LlmRoutingService;
import com.aipaper.tools.MeetingBookingTool;
import com.aipaper.validation.LlmResponseValidator;
import org.springframework.stereotype.Service;

@Service
public class LlmCommandExecutionService {

    private final LlmRoutingService routingService;
    private final MeetingRepository meetingRepository;
    private final LlmResponseValidator validator;

    public LlmCommandExecutionService(LlmRoutingService routingService,
                                      MeetingRepository meetingRepository,
                                      LlmResponseValidator validator) {
        this.routingService = routingService;
        this.meetingRepository = meetingRepository;
        this.validator = validator;
    }

    public MeetingBookingResult bookMeeting(LlmProvider provider, MeetingBookingRequest request) {
        String participantsStr = request.participants() != null
                ? String.join(", ", request.participants())
                : "none";

        String prompt = String.format(
                "Book a meeting with the following details. Validate all parameters before booking. " +
                "Use the bookMeeting tool to insert the meeting into the database.\n\n" +
                "Title: %s\n" +
                "Organizer email: %s\n" +
                "Participants: %s\n" +
                "Date: %s\n" +
                "Start time: %s\n" +
                "End time: %s\n" +
                "Location: %s\n\n" +
                "If any parameter is invalid (bad email, invalid date/time, end before start), " +
                "do NOT call the tool — instead return a JSON object with " +
                "{\"success\":false, \"meetingId\":null, \"message\":\"<reason>\"}.\n" +
                "If the booking succeeds, return the tool's result as JSON with fields: " +
                "success, meetingId, message.",
                request.title(),
                request.organizerEmail(),
                participantsStr,
                request.date(),
                request.startTime(),
                request.endTime(),
                request.location());

        try {
            MeetingBookingResult result = routingService.getClient(provider)
                    .prompt()
                    .user(prompt)
                    .tools(new MeetingBookingTool(meetingRepository))
                    .call()
                    .entity(MeetingBookingResult.class);

            validator.validate(result);
            return result;

        } catch (SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaValidationException(
                    "LLM command execution failed: " + e.getMessage(), e);
        }
    }
}
