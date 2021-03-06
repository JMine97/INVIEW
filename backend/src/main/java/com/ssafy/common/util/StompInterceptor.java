package com.ssafy.common.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.MultiValueMap;

import com.ssafy.api.service.ChatMessageService;
import com.ssafy.api.service.UserService;
import com.ssafy.api.service.meeting.MeetingService;
import com.ssafy.common.util.bean.ChattingParticipant;
import com.ssafy.db.entity.ChatMessage;
import com.ssafy.db.entity.ChatMessage.CommandType;
import com.ssafy.db.entity.User;
import com.ssafy.db.entity.meeting.Meeting;
import com.ssafy.db.entity.meeting.Status;

@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class StompInterceptor implements ChannelInterceptor {
	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private MeetingParticipant meetingParticipant;

	@Autowired
	private UserService userService;

	@Autowired
	private MeetingService meetingService;

	@Autowired
	private ChatMessageService chatMessageService;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
		String sessionId = headerAccessor.getSessionId();

		switch (headerAccessor.getCommand()) {
		case CONNECT:
			MessageHeaders headers = message.getHeaders();
			MultiValueMap<String, String> multiValueMap = headers.get(StompHeaderAccessor.NATIVE_HEADERS,
					MultiValueMap.class);
			HashMap<String, String> hashMap = new HashMap<>();
			String match = "[\\[\\],]";

			for (Entry<String, List<String>> head : multiValueMap.entrySet()) {
				hashMap.put(head.getKey(), String.valueOf(head.getValue()).replaceAll(match, ""));
			}

			String token = Objects.requireNonNull(hashMap.get("Authorization"));
			jwtTokenUtil.handleError(token);
			String email = Objects.requireNonNull(jwtTokenUtil.getUserEmailFromJwt(token));
			String meetingId = Objects.requireNonNull(hashMap.get("meetingId"));

			User user = userService.getUserByEmail(email);
			if (!meetingParticipant.checkParticipant(meetingId, email)) {
				// to do write
			}
			meetingParticipant.addParticipantBySessionId(sessionId, meetingId, user);
			break;
		case SEND:
			final StompHeaderAccessor accessor = StompHeaderAccessor.create(headerAccessor.getCommand());
			accessor.setSessionId(headerAccessor.getSessionId());
			break;
		}

		return message;
	}

	@Override
	public void postSend(Message message, MessageChannel channel, boolean sent) {
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
		String sessionId = headerAccessor.getSessionId();
		ChattingParticipant participant;
		ChattingParticipant host;
		String meetingId;

		switch (headerAccessor.getCommand()) {
		case CONNECT:
			participant = meetingParticipant.getParticipantBySessionId(sessionId);
			if (participant == null)
				break;

			// ????????? Websocket?????? connect()??? ??? ??? ?????????
			chatMessageService.sendCommandMessage(ChatMessage.builder().command(CommandType.CONNECT)
					.meetingId(participant.getMeetingId()).sessionId(participant.getSessionId())
					.sender(participant.getUser().getNickname()).message("").build(), sessionId);
			break;
		case SUBSCRIBE:
			participant = meetingParticipant.getParticipantBySessionId(sessionId);
			if (participant == null)
				break;

			meetingId = participant.getMeetingId();
			host = meetingParticipant.getHostByMeetingId(meetingId);
			User user = meetingService.getMeetingById(Integer.parseInt(meetingId)).getUser();
			// ????????? Websocket?????? subscribe() ??? ??? ??? ?????????
			CommandType commandType = participant.getSessionId() == host.getSessionId() ? CommandType.READY
					: CommandType.UNREADY;
			chatMessageService.sendCommandMessage(ChatMessage.builder().command(commandType)
					.meetingId(participant.getMeetingId()).sessionId(participant.getSessionId())
					.sender(participant.getUser().getNickname()).message("").build(), sessionId);

			chatMessageService.sendCommandMessage(ChatMessage.builder().command(CommandType.HOST)
					.meetingId(host.getMeetingId()).sessionId(host.getSessionId())
					.sender(String.valueOf(host.getUser().getUserId())).message(host.getUser().getNickname()).build(), host.getSessionId());
			break;
		case UNSUBSCRIBE: // ????????? Websocket?????? UNSUBSCRIBE() ??? ??? ??? ?????????
		case DISCONNECT:
			// ????????? Websocket?????? disconnect() ??? ??? ??? ????????? or ????????? ???????????? ??? ?????????(????????? ??????~ ???????????? ?????? ???)
			participant = meetingParticipant.deleteParticipantBySessionId(sessionId);
			if (participant == null)
				break;

			meetingId = participant.getMeetingId();
			host = meetingParticipant.getHostByMeetingId(meetingId);
			chatMessageService.sendCommandMessage(ChatMessage.builder().command(CommandType.DISCONNECT)
					.meetingId(participant.getMeetingId()).sessionId(participant.getSessionId())
					.sender(participant.getUser().getNickname()).message("").build(), sessionId);

			if (host != null && host.getSessionId().equals(sessionId)) {
				// ???????????? ??? ????????? ?????? ??????, ????????? ????????? ????????????.
				Meeting meeting = meetingService.getMeetingById(Integer.parseInt(meetingId));
				if (!meeting.getStatus().equals(Status.CLOSING)) {
					host = meetingParticipant.setHost(meetingId, host);
					chatMessageService.sendCommandMessage(ChatMessage.builder().command(CommandType.HOST)
							.meetingId(host.getMeetingId()).sessionId(host.getSessionId())
							.sender(String.valueOf(host.getUser().getUserId())).message(host.getUser().getNickname()).build(), host.getSessionId());
				}
			}
			
			chatMessageService.sendCommandMessage(ChatMessage.builder().command(CommandType.PARTICIPANT)
					.meetingId(participant.getMeetingId()).sessionId(participant.getSessionId())
					.sender(participant.getUser().getNickname()).message("").build(), sessionId);
			break;
		default:
			break;
		}
	}
}
