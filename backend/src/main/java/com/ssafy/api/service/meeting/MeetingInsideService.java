package com.ssafy.api.service.meeting;

import java.util.Optional;

import com.ssafy.db.entity.meeting.Meeting;

/**
 * 미팅 내부 관련 비즈니스 로직 처리를 위한 서비스 인터페이스 정의.
 */
public interface MeetingInsideService {
	void closeMeeting(int meetingId);
	Meeting getMeeting(int meetingId);
}