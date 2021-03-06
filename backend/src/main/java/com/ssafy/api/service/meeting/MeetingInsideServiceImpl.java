package com.ssafy.api.service.meeting;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.api.service.ChatMessageService;
import com.ssafy.common.exception.handler.NotExistsMeetingException;
import com.ssafy.common.exception.handler.NotExistsUserException;
import com.ssafy.common.exception.handler.NotValidMeetingStatusException;
import com.ssafy.common.util.MeetingParticipant;
import com.ssafy.common.util.bean.ChattingParticipant;
import com.ssafy.db.entity.ChatMessage;
import com.ssafy.db.entity.ChatMessage.CommandType;
import com.ssafy.db.entity.Participant;
import com.ssafy.db.entity.meeting.Meeting;
import com.ssafy.db.entity.meeting.Status;
import com.ssafy.db.repository.MeetingRepository;
import com.ssafy.db.repository.ParticipantRepository;
import com.ssafy.db.repository.UserRepository;

@Service("meetingInsideService")
public class MeetingInsideServiceImpl implements MeetingInsideService {

	@Autowired
	MeetingRepository meetingRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ParticipantRepository participantRepository;

	@Autowired
	MeetingParticipant meetingParticipant;

	@Autowired
	ChatMessageService chatMessageService;
	
	@Transactional
	@Override
	public void startMeeting(int meetingId, int hostId) {
		// 미팅 시작 시간 삽입 및 상태 변경
		Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(() -> new NotExistsMeetingException());

//		if (meeting.getUser().getUserId() != hostId) {
//			throw new NotHostException();
//		}

		if (!meeting.getStatus().equals(Status.WAITING)) {
			throw new NotValidMeetingStatusException();
		}

		meeting.setStartTime(LocalDateTime.now());
		meeting.setStatus(Status.RUNNING);
	}

	@Transactional
	@Override
	public void closeMeeting(int meetingId, int hostId) {
		// 미팅 종료 시간 삽입 및 상태 변경
		Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(() -> new NotExistsMeetingException());

		if (!meeting.getStatus().equals(Status.RUNNING)) {
			throw new NotValidMeetingStatusException();
		}

		LocalDateTime localDateTime = LocalDateTime.now();
		meeting.setEndTime(localDateTime);
		meeting.setCloseTime(localDateTime);
		meeting.setStatus(Status.CLOSING);
	}

	@Override
	public Meeting getMeeting(int meetingId) {
		Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(() -> new NotExistsMeetingException());
		return meeting;
	}

	@Override
	@Transactional
	public void quitParticipant(int meetingId, int userId) {
		// DB에서 userId 삭제
		meetingRepository.findById(meetingId).orElseThrow(() -> new NotExistsMeetingException());

		Participant participant = this.checkParticipant(meetingId, userId);

		participantRepository.delete(participant);
	}

	@Override
	public Participant checkParticipant(int meetingId, int userId) {
		Participant participant = participantRepository.findByMeetingIdAndUserId(meetingId, userId)
				.orElseThrow(() -> new NotExistsUserException());
		return participant;
	}

	@Override
	@Transactional
	@Modifying
	public void forcedExit(int meetingId, int userId) {
		meetingRepository.findById(meetingId).orElseThrow(() -> new NotExistsMeetingException());

		Participant participant = this.checkParticipant(meetingId, userId);
		participant.setForcedExit(1);
		participantRepository.updateForcedExit(meetingId, userId);
		
		String strMeetingId = String.valueOf(meetingId);
		String strUserId = String.valueOf(userId);
		List<ChattingParticipant> deleteParticipant = meetingParticipant
				.deleteParticipantByMeetingId(strMeetingId, strUserId);
		
		for (ChattingParticipant chattingParticipant : deleteParticipant) {
			chatMessageService.sendCommandMessage(ChatMessage.builder().command(CommandType.OUT).meetingId(strMeetingId)
					.sessionId(chattingParticipant.getSessionId()).sender(strUserId).build(), chattingParticipant.getSessionId());
		}
	}

	@Override
	public int getMeetingIdByUrl(String meetingUrl) {
		Meeting meeting = meetingRepository.findByUrl(meetingUrl).orElseThrow(() -> new NotExistsMeetingException());

		return meeting.getMeetingId();
	}

	@Override
	public void checkForcedExit(int meetingId, int userId) {
		Long count = participantRepository.findCountByMeetingIdAndUserId(meetingId, userId);
		if (count != 0)
			new NotExistsUserException();
	}

}
