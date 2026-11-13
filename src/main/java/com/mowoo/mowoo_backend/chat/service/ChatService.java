package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.character.repository.UserCharacterRepository;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatAiException;
import com.mowoo.mowoo_backend.chat.ai.ChatAiRequest;
import com.mowoo.mowoo_backend.chat.ai.ChatSummaryAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiaryComparisonAiClient;
import com.mowoo.mowoo_backend.chat.ai.DiarySuggestionAiClient;
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
import com.mowoo.mowoo_backend.chat.entity.DiaryReference;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.chat.exception.ChatErrorCode;
import com.mowoo.mowoo_backend.chat.repository.ChatMessageRepository;
import com.mowoo.mowoo_backend.chat.repository.ChatSessionRepository;
import com.mowoo.mowoo_backend.chat.repository.DiaryReferenceRepository;
import com.mowoo.mowoo_backend.chat.repository.SessionSenderRole;
import com.mowoo.mowoo_backend.diary.entity.Diary;
import com.mowoo.mowoo_backend.diary.exception.DiaryErrorCode;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import com.mowoo.mowoo_backend.user.entity.User;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final ChatSummaryAiClient.Summary NO_SUMMARY = new ChatSummaryAiClient.Summary(null, null);
    // AI에 후보로 넘길 최근 저장 일기 수와 돌려줄 후보 수
    private static final int SUGGESTION_CANDIDATE_LIMIT = 30;
    private static final int SUGGESTION_LIMIT = 3;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DiaryReferenceRepository diaryReferenceRepository;
    private final DiaryRepository diaryRepository;
    private final UserCharacterRepository userCharacterRepository;
    private final UserRepository userRepository;
    private final ChatAiClient chatAiClient;
    private final ChatSummaryAiClient chatSummaryAiClient;
    private final DiarySuggestionAiClient diarySuggestionAiClient;
    private final DiaryComparisonAiClient diaryComparisonAiClient;
    private final EntityManager entityManager;

    @Transactional
    public ChatSessionResponse startSession(UUID userId, StartChatSessionRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        UUID characterId = request.characterId();
        if (!userCharacterRepository.existsById(new UserCharacter.Id(userId, characterId))) {
            throw new BusinessException(ChatErrorCode.CHARACTER_NOT_OWNED);
        }

        // 보유 기록이 있으면 사용자와 캐릭터가 모두 있으므로 조회 없이 참조만 건다.
        ChatSession session = sessionRepository.saveAndFlush(new ChatSession(
                user,
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
        Map<UUID, List<ChatMessageResponse.DiaryReference>> referencesByMessage = findDiaryReferencesByMessage(page);
        List<ChatMessageResponse> items = page.stream()
                .map(message -> toMessageResponse(message, referencesByMessage.getOrDefault(message.getId(), List.of())))
                .toList();

        return new ChatSessionDetailResponse(
                toSessionItem(session, countCompletedTurns(sessionId)),
                new ChatMessagePage(items, nextCursor)
        );
    }

    // 처음 종료할 때만 대화 기록으로 제목과 요약을 만든다. 이미 종료된 대화는 기존 결과를 그대로 돌려준다.
    // 같은 대화를 동시에 종료해도 요약을 한 번만 만들도록 세션 행을 잠근다.
    @Transactional
    public ChatSessionResponse endSession(UUID userId, UUID sessionId) {
        ChatSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.SESSION_NOT_FOUND));
        if (!session.isEnded()) {
            // DATETIME은 초 단위로 저장되므로 응답과 저장 값이 같도록 초 단위로 자른다.
            Instant endedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            ChatSummaryAiClient.Summary summary = summarize(sessionId);
            session.end(summary.title(), summary.summary(), endedAt);
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

    // 최근 저장 일기 중에서 AI가 현재 고민과 관련된 일기를 고른다.
    // 명세에 AI 실패 응답이 없으므로 고르지 못하면 빈 목록을 돌려준다.
    public ListResponse<DiarySuggestionResponse> getDiarySuggestions(UUID userId, UUID sessionId, UUID messageId) {
        requirePastDiariesEnabled(userId);
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));
        ChatMessage concern = findUserMessage(session, messageId);

        // 이 대화로 만든 일기는 지금 고민과 같은 내용이라 후보에서 뺀다.
        List<Diary> candidates = diaryRepository.findSaved(userId, null, null, Limit.of(SUGGESTION_CANDIDATE_LIMIT))
                .stream()
                .filter(diary -> diary.getSourceSession() == null || !diary.getSourceSession().getId().equals(sessionId))
                .toList();
        List<DiarySuggestionResponse> items = selectRelatedDiaries(sessionId, concern.getContent(), candidates).stream()
                .map(diary -> new DiarySuggestionResponse(diary.getId(), diary.getEntryDate(), diary.getTitle()))
                .toList();
        return new ListResponse<>(CommonSuccessCode.DIARY_SUGGESTION_READ_SUCCESS, items);
    }

    // 고민 메시지와 사용자가 고른 저장 일기를 비교한 답변을 대화 끝에 AI 메시지로 저장하고 일기 참조를 단다.
    // 이 답변은 사용자 메시지 없이 추가되므로 왕복 수에 들어가지 않는다(ChatTurnPolicy).
    // 일반 조회보다 세션 잠금을 먼저 얻어야, 기다리는 동안 다른 요청이 저장한 최신 순번을 읽는다.
    // 고른 일기는 공유 잠금으로 읽어서 연결이 끝날 때까지 삭제를 막는다. 같은 일기를 연결하는 다른 대화는 기다리지 않는다.
    @Transactional
    public CreateDiaryReferenceResponse createDiaryReference(
            UUID userId,
            UUID sessionId,
            CreateDiaryReferenceRequest request
    ) {
        ChatSession session = sessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));
        requirePastDiariesEnabled(userId);
        if (session.isEnded()) {
            throw new BusinessException(ChatErrorCode.SESSION_CLOSED);
        }
        ChatMessage concern = findUserMessage(session, request.messageId());
        Diary diary = entityManager.find(Diary.class, request.diaryId(), LockModeType.PESSIMISTIC_READ);
        if (diary == null || !diary.getUser().getId().equals(userId) || diary.getSavedAt() == null) {
            throw new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND);
        }

        // 지금 대화 중인 캐릭터(마지막 AI 응답의 캐릭터)로 답한다.
        ChatMessage latest = messageRepository.findTopBySessionIdOrderBySequenceNoDesc(sessionId).orElseThrow();
        AiCharacter character = latest.getCharacter() != null ? latest.getCharacter() : session.getCharacter();
        String reply;
        try {
            reply = diaryComparisonAiClient.compare(new DiaryComparisonAiClient.Request(
                    character.getName(), character.getDescription(), concern.getContent(),
                    diary.getEntryDate(), diary.getTitle(), diary.getContent()));
        } catch (ChatAiException e) {
            throw new BusinessException(ChatErrorCode.AI_UNAVAILABLE);
        }

        ChatMessage assistantMessage = messageRepository.saveAndFlush(
                ChatMessage.ofAssistant(session, latest.getSequenceNo() + 1, character, reply));
        // DATETIME은 초 단위로 저장되므로 다시 읽어서 이후 조회와 같은 createdAt을 응답한다.
        entityManager.refresh(assistantMessage);
        DiaryReference reference = diaryReferenceRepository.save(new DiaryReference(
                assistantMessage, diary, Instant.now().truncatedTo(ChronoUnit.SECONDS)));

        return new CreateDiaryReferenceResponse(toMessageResponse(assistantMessage, List.of(toDiaryReference(reference))));
    }

    private void requirePastDiariesEnabled(UUID userId) {
        User user = entityManager.find(User.class, userId);
        if (user == null || !user.isUsePastDiaries()) {
            throw new BusinessException(ChatErrorCode.PAST_DIARY_DISABLED);
        }
    }

    // 과거 일기는 사용자가 보낸 메시지에만 연결할 수 있다.
    private ChatMessage findUserMessage(ChatSession session, UUID messageId) {
        return messageRepository.findByIdAndSessionId(messageId, session.getId())
                .filter(message -> message.getSenderRole() == SenderRole.USER)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND));
    }

    private List<Diary> selectRelatedDiaries(UUID sessionId, String concern, List<Diary> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            return diarySuggestionAiClient.select(concern, candidates.stream()
                            .map(diary -> new DiarySuggestionAiClient.Candidate(
                                    diary.getEntryDate(), diary.getTitle(), diary.getContent()))
                            .toList(), SUGGESTION_LIMIT).stream()
                    .map(candidates::get)
                    .toList();
        } catch (ChatAiException e) {
            log.warn("과거 일기 후보를 고르지 못해 빈 목록을 돌려줍니다. sessionId={}", sessionId);
            return List.of();
        }
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
        ChatAiRequest request = new ChatAiRequest(character.getName(), character.getDescription(), toAiMessages(history));
        try {
            return chatAiClient.reply(request);
        } catch (ChatAiException e) {
            throw new BusinessException(ChatErrorCode.AI_UNAVAILABLE);
        }
    }

    // 명세상 요약 생성에 실패해도 종료는 성공하고 제목과 요약은 null로 둔다.
    private ChatSummaryAiClient.Summary summarize(UUID sessionId) {
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNo(sessionId);
        if (messages.isEmpty()) {
            return NO_SUMMARY;
        }
        try {
            return chatSummaryAiClient.summarize(toAiMessages(messages));
        } catch (ChatAiException e) {
            log.warn("대화 요약을 만들지 못해 제목과 요약 없이 종료합니다. sessionId={}", sessionId);
            return NO_SUMMARY;
        }
    }

    private List<ChatAiRequest.Message> toAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> new ChatAiRequest.Message(message.getSenderRole(), message.getContent()))
                .toList();
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

    // 메시지 전송·수정 응답의 메시지에는 과거 일기 참조가 없다. 참조는 비교 답변에만 달린다.
    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return toMessageResponse(message, List.of());
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message, List<ChatMessageResponse.DiaryReference> references) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSequenceNo(),
                message.getSenderRole(),
                message.getCharacter() == null ? null : message.getCharacter().getId(),
                message.getContent(),
                message.getCreatedAt(),
                message.getEditedAt(),
                references
        );
    }

    private Map<UUID, List<ChatMessageResponse.DiaryReference>> findDiaryReferencesByMessage(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        List<UUID> messageIds = messages.stream().map(ChatMessage::getId).toList();
        return diaryReferenceRepository.findWithDiaryByMessageIds(messageIds).stream()
                .collect(Collectors.groupingBy(
                        reference -> reference.getId().messageId(),
                        Collectors.mapping(this::toDiaryReference, Collectors.toList())
                ));
    }

    private ChatMessageResponse.DiaryReference toDiaryReference(DiaryReference reference) {
        Diary diary = reference.getDiary();
        return new ChatMessageResponse.DiaryReference(
                diary.getId(), diary.getEntryDate(), diary.getTitle(), reference.getAcceptedAt());
    }
}
