package com.aipaper.service.scenario;

import com.aipaper.dto.MeetingBookingRequest;
import com.aipaper.dto.MeetingBookingResult;

public interface CommandExecutionService {

    MeetingBookingResult bookMeeting(MeetingBookingRequest request);
}
