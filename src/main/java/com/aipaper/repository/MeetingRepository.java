package com.aipaper.repository;

import com.aipaper.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByOrganizerEmail(String organizerEmail);

    List<Meeting> findByMeetingDate(LocalDate meetingDate);
}
