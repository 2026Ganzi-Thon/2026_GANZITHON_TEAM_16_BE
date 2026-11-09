package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.chat.dto.response.DiarySuggestionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.UpdateMessageResponse;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.chat.repository.CharacterOwnershipRepository;
import com.mowoo.mowoo_backend.chat.repository.ChatMessageRepository;
import com.mowoo.mowoo_backend.chat.repository.ChatSessionRepository;
import com.mowoo.mowoo_backend.chat.repository.SessionSenderRole;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import com.mowoo.mowoo_backend.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// 구현 전 기능은 UnsupportedOperationException을 던진다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final CharacterOwnershipRepository characterOwnershipRepository;
    private final EntityManager entityManager;

    @Transactional
    public ChatSessionResponse startSession(UUID userId, StartChatSessionRequest request) {
        UUID characterId = request.characterId();
        if (!characterOwnershipRepository.existsById(new UserCharacter.Id(userId, characterId))) {
            throw new BusinessException(ChatErrorCode.CHARACTER_NOT_OWNED);
        }

        // 보유 기록이 있으면 사용자와 캐릭터가 모두 있으므로 조회 없이 참조만 건다.
        ChatSession session = sessionRepository.saveAndFlush(new ChatSession(
                entityManager.getReference(User.class, userId),
                entityManager.getReference(AiCharacter.class, characterId)
        ));
        // DATETIME은 초 단위로 저장되므로 다시 읽어서 이후 조회와 같은 startedAt을 응답한다.
        entityManager.refresh(session);
        return toSessionResponse(session, 0);
    }

    public CursorResponse<ChatSessionResponse> getSessions(UUID userId, CursorRequest cursorRequest) {
        int limit = cursorRequest.limit();
        Limit fetchLimit = Limit.of(limit + 1);

        List<ChatSession> sessions;
        if (cursorRequest.cursor() == null) {
            sessions = sessionRepository.findLatest(userId, fetchLimit);
        } else {
            ChatCursor.SessionCursor cursor = ChatCursor.decodeSession(cursorRequest.cursor());
            sessions = sessionRepository.findLatestAfter(userId, cursor.startedAt(), cursor.id(), fetchLimit);
        }

        boolean hasNext = sessions.size() > limit;
        List<ChatSession> page = hasNext ? sessions.subList(0, limit) : sessions;

        Map<UUID, List<SenderRole>> senderRolesBySession = findSenderRolesBySession(page);
        List<ChatSessionResponse> items = page.stream()
                .map(session -> toSessionResponse(session, ChatTurnPolicy.countCompletedTurns(
                        senderRolesBySession.getOrDefault(session.getId(), List.of()))))
                .toList();

        String nextCursor = null;
        if (hasNext) {
            ChatSession last = page.getLast();
            nextCursor = ChatCursor.encodeSession(last.getStartedAt(), last.getId());
        }
        return new CursorResponse<>(items, nextCursor);
    }

    // 메시지는 최근 것부터 페이지를 나누고, 한 페이지 안에서는 순번 오름차순으로 돌려준다.
    public ChatSessionDetailResponse getSession(UUID userId, UUID sessionId, CursorRequest cursorRequest) {
        ChatSession session = findOwnedSession(userId, sessionId);
        int limit = cursorRequest.limit();
        Limit fetchLimit = Limit.of(limit + 1);

        List<ChatMessage> messages;
        if (cursorRequest.cursor() == null) {
            messages = messageRepository.findLatest(sessionId, fetchLimit);
        } else {
            int sequenceNo = ChatCursor.decodeMessage(cursorRequest.cursor());
            messages = messageRepository.findLatestBefore(sessionId, sequenceNo, fetchLimit);
        }

        boolean hasNext = messages.size() > limit;
        List<ChatMessage> page = new ArrayList<>(hasNext ? messages.subList(0, limit) : messages);
        Collections.reverse(page);

        String nextCursor = hasNext ? ChatCursor.encodeMessage(page.getFirst().getSequenceNo()) : null;
        List<ChatMessageResponse> items = page.stream()
                .map(this::toMessageResponse)
                .toList();

        return new ChatSessionDetailResponse(
                toSessionResponse(session, countCompletedTurns(sessionId)),
                new CursorResponse<>(items, nextCursor)
        );
    }

    // 이미 종료된 대화는 기존 결과를 그대로 돌려준다.
    @Transactional
    public ChatSessionResponse endSession(UUID userId, UUID sessionId) {
        ChatSession session = findOwnedSession(userId, sessionId);
        if (!session.isEnded()) {
            // 제목과 요약은 AI 연결 후 채운다. 명세상 요약 생성에 실패하면 null로 둔다.
            // DATETIME은 초 단위로 저장되므로 응답과 저장 값이 같도록 초 단위로 자른다.
            session.end(null, null, Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
        return toSessionResponse(session, countCompletedTurns(sessionId));
    }

    // 메시지와 첨부 행은 DB 외래키(ON DELETE CASCADE)로 함께 지워진다.
    // 저장소의 첨부 파일 정리는 첨부 저장 기능을 연결할 때 추가한다.
    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        sessionRepository.delete(findOwnedSession(userId, sessionId));
    }

    public SendMessageResponse sendMessage(
            UUID userId,
            UUID sessionId,
            SendMessageRequest request,
            List<MultipartFile> files
    ) {
        throw notImplemented();
    }

    public UpdateMessageResponse updateMessage(
            UUID userId,
            UUID sessionId,
            UUID messageId,
            UpdateMessageRequest request
    ) {
        throw notImplemented();
    }

    public ListResponse<DiarySuggestionResponse> getDiarySuggestions(UUID userId, UUID sessionId, UUID messageId) {
        throw notImplemented();
    }

    public CreateDiaryReferenceResponse createDiaryReference(
            UUID userId,
            UUID sessionId,
            CreateDiaryReferenceRequest request
    ) {
        throw notImplemented();
    }

    private ChatSession findOwnedSession(UUID userId, UUID sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.SESSION_NOT_FOUND));
    }

    private int countCompletedTurns(UUID sessionId) {
        return ChatTurnPolicy.countCompletedTurns(messageRepository.findSenderRolesBySessionId(sessionId));
    }

    private Map<UUID, List<SenderRole>> findSenderRolesBySession(List<ChatSession> sessions) {
        if (sessions.isEmpty()) {
            return Map.of();
        }
        List<UUID> sessionIds = sessions.stream().map(ChatSession::getId).toList();
        return messageRepository.findSenderRolesBySessionIds(sessionIds).stream()
                .collect(Collectors.groupingBy(
                        SessionSenderRole::sessionId,
                        Collectors.mapping(SessionSenderRole::senderRole, Collectors.toList())
                ));
    }

    private ChatSessionResponse toSessionResponse(ChatSession session, int completedTurns) {
        return new ChatSessionResponse(
                session.getId(),
                session.getCharacter().getId(),
                session.getTitle(),
                session.getSummary(),
                session.getStartedAt(),
                session.getEndedAt(),
                completedTurns,
                ChatTurnPolicy.canGenerateDiary(completedTurns)
        );
    }

    // 첨부는 저장소 연결 후 만료 URL과 함께, 과거 일기 참조는 일기 엔티티가 생긴 뒤 채운다.
    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSequenceNo(),
                message.getSenderRole(),
                message.getCharacter() == null ? null : message.getCharacter().getId(),
                message.getContent(),
                message.getCreatedAt(),
                message.getEditedAt(),
                List.of(),
                List.of()
        );
    }

    private UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("구현 예정");
    }
}
