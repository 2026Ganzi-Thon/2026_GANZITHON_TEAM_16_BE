package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.character.repository.UserCharacterRepository;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatAiException;
import com.mowoo.mowoo_backend.chat.ai.ChatAiRequest;
import com.mowoo.mowoo_backend.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatMessagePage;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionItem;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.chat.dto.response.DiarySuggestionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.UpdateMessageResponse;
import com.mowoo.mowoo_backend.chat.entity.ChatMessage;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.chat.repository.ChatMessageRepository;
import com.mowoo.mowoo_backend.chat.repository.ChatSessionRepository;
import com.mowoo.mowoo_backend.chat.repository.SessionSenderRole;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import com.mowoo.mowoo_backend.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserCharacterRepository userCharacterRepository;
    private final ChatAiClient chatAiClient;
    private final EntityManager entityManager;

    @Transactional
    public ChatSessionResponse startSession(UUID userId, StartChatSessionRequest request) {
        UUID characterId = request.characterId();
        if (!userCharacterRepository.existsById(new UserCharacter.Id(userId, characterId))) {
            throw new BusinessException(ChatErrorCode.CHARACTER_NOT_OWNED);
        }

        // 보유 기록이 있으면 사용자와 캐릭터가 모두 있으므로 조회 없이 참조만 건다.
        ChatSession session = sessionRepository.saveAndFlush(new ChatSession(
                entityManager.getReference(User.class, userId),
                entityManager.getReference(AiCharacter.class, characterId)
        ));
        // DATETIME은 초 단위로 저장되므로 다시 읽어서 이후 조회와 같은 startedAt을 응답한다.
        entityManager.refresh(session);
        return ChatSessionResponse.of(CommonSuccessCode.CHAT_SESSION_START_SUCCESS, toSessionItem(session, 0));
    }

    public CursorResponse<ChatSessionItem> getSessions(UUID userId, CursorRequest cursorRequest) {
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
        List<ChatSessionItem> items = page.stream()
                .map(session -> toSessionItem(session, ChatTurnPolicy.countCompletedTurns(
                        senderRolesBySession.getOrDefault(session.getId(), List.of()))))
                .toList();

        String nextCursor = null;
        if (hasNext) {
            ChatSession last = page.getLast();
            nextCursor = ChatCursor.encodeSession(last.getStartedAt(), last.getId());
        }
        return new CursorResponse<>(CommonSuccessCode.CHAT_SESSION_LIST_READ_SUCCESS, items, nextCursor);
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
                toSessionItem(session, countCompletedTurns(sessionId)),
                new ChatMessagePage(items, nextCursor)
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
        return ChatSessionResponse.of(
                CommonSuccessCode.CHAT_SESSION_END_SUCCESS, toSessionItem(session, countCompletedTurns(sessionId)));
    }

    // 메시지는 DB 외래키(ON DELETE CASCADE)로 함께 지워진다.
    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        sessionRepository.delete(findOwnedSession(userId, sessionId));
    }

    @Transactional
    public void deleteAllSessions(UUID userId) {
        sessionRepository.deleteAllForUser(userId);
    }

    // AI 응답까지 만들어야 한 왕복으로 저장한다. AI가 실패하면 사용자 메시지도 저장하지 않는다(롤백).
    @Transactional
    public SendMessageResponse sendMessage(
            UUID userId,
            UUID sessionId,
            SendMessageRequest request
    ) {
        if (!request.hasContent()) {
            throw new BusinessException(ChatErrorCode.MESSAGE_EMPTY);
        }

        ChatSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.SESSION_NOT_FOUND));
        if (session.isEnded()) {
            throw new BusinessException(ChatErrorCode.SESSION_CLOSED);
        }
        AiCharacter character = resolveReplyCharacter(userId, session, request.characterId());

        int nextSequenceNo = messageRepository.findTopBySessionIdOrderBySequenceNoDesc(sessionId)
                .map(latest -> latest.getSequenceNo() + 1)
                .orElse(1);
        ChatMessage userMessage = messageRepository.save(
                ChatMessage.ofUser(session, nextSequenceNo, request.content()));

        String reply = generateReply(character, messageRepository.findBySessionIdOrderBySequenceNo(sessionId));
        ChatMessage assistantMessage = messageRepository.saveAndFlush(
                ChatMessage.ofAssistant(session, nextSequenceNo + 1, character, reply));
        // DATETIME은 초 단위로 저장되므로 다시 읽어서 이후 조회와 같은 createdAt을 응답한다.
        entityManager.refresh(userMessage);
        entityManager.refresh(assistantMessage);

        int completedTurns = countCompletedTurns(sessionId);
        return new SendMessageResponse(
                toMessageResponse(userMessage),
                toMessageResponse(assistantMessage),
                completedTurns,
                ChatTurnPolicy.canGenerateDiary(completedTurns)
        );
    }

    // 가장 최근 왕복의 사용자 메시지만 고칠 수 있다. 그 뒤의 AI 응답은 같은 캐릭터로 다시 만든다.
    @Transactional
    public UpdateMessageResponse updateMessage(
            UUID userId,
            UUID sessionId,
            UUID messageId,
            UpdateMessageRequest request
    ) {
        // 명세상 다른 사용자의 세션이나 없는 세션도 MESSAGE_NOT_FOUND로 응답한다.
        ChatSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));
        ChatMessage userMessage = messageRepository.findByIdAndSessionId(messageId, sessionId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));
        if (session.isEnded()) {
            throw new BusinessException(ChatErrorCode.SESSION_CLOSED);
        }

        // 마지막 메시지가 이 사용자 메시지에 대한 AI 응답이어야 한다.
        ChatMessage latest = messageRepository.findTopBySessionIdOrderBySequenceNoDesc(sessionId).orElseThrow();
        boolean isLatestTurn = userMessage.getSenderRole() == SenderRole.USER
                && latest.getSenderRole() == SenderRole.ASSISTANT
                && latest.getSequenceNo() == userMessage.getSequenceNo() + 1;
        if (!isLatestTurn) {
            throw new BusinessException(ChatErrorCode.MESSAGE_NOT_EDITABLE);
        }

        userMessage.edit(request.content(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        List<ChatMessage> history = messageRepository.findBySessionIdOrderBySequenceNo(sessionId).stream()
                .filter(message -> message.getSequenceNo() <= userMessage.getSequenceNo())
                .toList();
        latest.replaceReply(generateReply(latest.getCharacter(), history));

        return new UpdateMessageResponse(toMessageResponse(userMessage), toMessageResponse(latest));
    }

    // 일기 파트의 AI 초안 생성(PUT /chat-sessions/{sessionId}/diary-draft)에서 호출한다.
    // 본인 대화가 아니면 명세대로 SESSION_NOT_FOUND를 던진다.
    public ChatTranscript getTranscript(UUID userId, UUID sessionId) {
        ChatSession session = findOwnedSession(userId, sessionId);
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNo(sessionId);
        int completedTurns = ChatTurnPolicy.countCompletedTurns(
                messages.stream().map(ChatMessage::getSenderRole).toList());

        return new ChatTranscript(
                session.getId(),
                session.getStartedAt(),
                session.getEndedAt(),
                completedTurns,
                ChatTurnPolicy.canGenerateDiary(completedTurns),
                messages.stream()
                        .map(message -> new ChatTranscript.Message(
                                message.getSequenceNo(),
                                message.getSenderRole(),
                                message.getCharacter() == null ? null : message.getCharacter().getName(),
                                message.getContent(),
                                message.getCreatedAt()))
                        .toList()
        );
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

    // 캐릭터를 지정하지 않으면 세션의 기본 캐릭터로 응답한다.
    private AiCharacter resolveReplyCharacter(UUID userId, ChatSession session, UUID characterId) {
        if (characterId == null || characterId.equals(session.getCharacter().getId())) {
            return session.getCharacter();
        }
        if (!userCharacterRepository.existsById(new UserCharacter.Id(userId, characterId))) {
            throw new BusinessException(ChatErrorCode.CHARACTER_NOT_OWNED);
        }
        return entityManager.find(AiCharacter.class, characterId);
    }

    private String generateReply(AiCharacter character, List<ChatMessage> history) {
        ChatAiRequest request = new ChatAiRequest(
                character.getName(),
                character.getDescription(),
                history.stream()
                        .map(message -> new ChatAiRequest.Message(message.getSenderRole(), message.getContent()))
                        .toList()
        );
        try {
            return chatAiClient.reply(request);
        } catch (ChatAiException e) {
            throw new BusinessException(ChatErrorCode.AI_UNAVAILABLE);
        }
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

    private ChatSessionItem toSessionItem(ChatSession session, int completedTurns) {
        return new ChatSessionItem(
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

    // 과거 일기 참조는 일기 엔티티가 생긴 뒤 채운다.
    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSequenceNo(),
                message.getSenderRole(),
                message.getCharacter() == null ? null : message.getCharacter().getId(),
                message.getContent(),
                message.getCreatedAt(),
                message.getEditedAt(),
                List.of()
        );
    }

    private UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("구현 예정");
    }
}
