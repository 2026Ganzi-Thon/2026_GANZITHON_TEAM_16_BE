package com.mowoo.mowoo_backend.domain.chat.dto;

import com.mowoo.mowoo_backend.domain.chat.dto.request.ChatSessionAction;
import com.mowoo.mowoo_backend.domain.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.request.UpdateChatSessionRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.response.ChatMessageResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.DiarySuggestionResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.domain.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 응답 DTO가 API 명세서(docs/api-spec.md)의 예시 JSON과 같은 모양으로 직렬화되는지 확인한다.
@JsonTest
class ChatDtoJsonTest {

    private static final UUID SESSION_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0011");
    private static final UUID CHARACTER_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0022");
    private static final UUID USER_MESSAGE_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0022");
    private static final UUID ASSISTANT_MESSAGE_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0033");
    private static final UUID DIARY_ID = UUID.fromString("a3c8bfe1-0b2a-4891-88c5-d4af752c0033");
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void startedSessionMatchesSpecExample() {
        ChatSessionResponse response = new ChatSessionResponse(
                SESSION_ID, CHARACTER_ID, null, null, NOW, null, 0, false);

        assertJsonEquals(response, """
                {
                  "id": "a3c8bfe1-0b2a-4891-88c5-d4af752c0011",
                  "characterId": "a3c8bfe1-0b2a-4891-88c5-d4af752c0022",
                  "title": null,
                  "summary": null,
                  "startedAt": "2026-11-07T12:00:00Z",
                  "endedAt": null,
                  "completedTurns": 0,
                  "canGenerateDiary": false
                }
                """);
    }

    @Test
    void endedSessionMatchesSpecExample() {
        ChatSessionResponse response = new ChatSessionResponse(
                SESSION_ID, CHARACTER_ID, "면접을 앞둔 날", "면접을 준비하며 느낀 걱정을 이야기했어요.",
                NOW, Instant.parse("2026-11-07T12:10:00Z"), 5, true);

        assertJsonEquals(response, """
                {
                  "id": "a3c8bfe1-0b2a-4891-88c5-d4af752c0011",
                  "characterId": "a3c8bfe1-0b2a-4891-88c5-d4af752c0022",
                  "title": "면접을 앞둔 날",
                  "summary": "면접을 준비하며 느낀 걱정을 이야기했어요.",
                  "startedAt": "2026-11-07T12:00:00Z",
                  "endedAt": "2026-11-07T12:10:00Z",
                  "completedTurns": 5,
                  "canGenerateDiary": true
                }
                """);
    }

    @Test
    void sentMessageMatchesSpecExample() {
        SendMessageResponse response = new SendMessageResponse(
                userMessage("오늘 면접이 걱정돼요."),
                assistantMessage(2, "어떤 점이 가장 걱정되나요?", List.of()),
                1,
                false);

        assertJsonEquals(response, """
                {
                  "userMessage": {
                    "id": "a3c8bfe1-0b2a-4891-88c5-d4af752c0022",
                    "sequenceNo": 1,
                    "senderRole": "USER",
                    "characterId": null,
                    "content": "오늘 면접이 걱정돼요.",
                    "createdAt": "2026-11-07T12:00:00Z",
                    "editedAt": null,
                    "attachments": [],
                    "diaryReferences": []
                  },
                  "assistantMessage": {
                    "id": "a3c8bfe1-0b2a-4891-88c5-d4af752c0033",
                    "sequenceNo": 2,
                    "senderRole": "ASSISTANT",
                    "characterId": "a3c8bfe1-0b2a-4891-88c5-d4af752c0022",
                    "content": "어떤 점이 가장 걱정되나요?",
                    "createdAt": "2026-11-07T12:00:00Z",
                    "editedAt": null,
                    "attachments": [],
                    "diaryReferences": []
                  },
                  "completedTurns": 1,
                  "canGenerateDiary": false
                }
                """);
    }

    @Test
    void sessionDetailWrapsMessagesInCursorPage() {
        ChatSessionDetailResponse response = new ChatSessionDetailResponse(
                new ChatSessionResponse(SESSION_ID, CHARACTER_ID, null, null, NOW, null, 0, false),
                new CursorResponse<>(List.of(userMessage("오늘 면접이 걱정돼요.")), null));

        assertJsonEquals(response, """
                {
                  "session": {
                    "id": "a3c8bfe1-0b2a-4891-88c5-d4af752c0011",
                    "characterId": "a3c8bfe1-0b2a-4891-88c5-d4af752c0022",
                    "title": null,
                    "summary": null,
                    "startedAt": "2026-11-07T12:00:00Z",
                    "endedAt": null,
                    "completedTurns": 0,
                    "canGenerateDiary": false
                  },
                  "messages": {
                    "items": [
                      {
                        "id": "a3c8bfe1-0b2a-4891-88c5-d4af752c0022",
                        "sequenceNo": 1,
                        "senderRole": "USER",
                        "characterId": null,
                        "content": "오늘 면접이 걱정돼요.",
                        "createdAt": "2026-11-07T12:00:00Z",
                        "editedAt": null,
                        "attachments": [],
                        "diaryReferences": []
                      }
                    ],
                    "nextCursor": null
                  }
                }
                """);
    }

    @Test
    void diaryReferenceMatchesSpecExample() {
        CreateDiaryReferenceResponse response = new CreateDiaryReferenceResponse(assistantMessage(
                3,
                "두 기록에서 타인의 평가가 걱정된다는 공통점이 보여요.",
                List.of(new ChatMessageResponse.DiaryReference(
                        DIARY_ID, LocalDate.parse("2026-09-15"), "발표를 앞둔 날", NOW))));

        assertJsonEquals(response, """
                {
                  "assistantMessage": {
                    "id": "a3c8bfe1-0b2a-4891-88c5-d4af752c0033",
                    "sequenceNo": 3,
                    "senderRole": "ASSISTANT",
                    "characterId": "a3c8bfe1-0b2a-4891-88c5-d4af752c0022",
                    "content": "두 기록에서 타인의 평가가 걱정된다는 공통점이 보여요.",
                    "createdAt": "2026-11-07T12:00:00Z",
                    "editedAt": null,
                    "attachments": [],
                    "diaryReferences": [
                      {
                        "diaryId": "a3c8bfe1-0b2a-4891-88c5-d4af752c0033",
                        "entryDate": "2026-09-15",
                        "title": "발표를 앞둔 날",
                        "acceptedAt": "2026-11-07T12:00:00Z"
                      }
                    ]
                  }
                }
                """);
    }

    @Test
    void diarySuggestionsMatchSpecExample() {
        ListResponse<DiarySuggestionResponse> response = new ListResponse<>(List.of(
                new DiarySuggestionResponse(DIARY_ID, LocalDate.parse("2026-09-15"), "발표를 앞둔 날")));

        assertJsonEquals(response, """
                {
                  "items": [
                    {
                      "diaryId": "a3c8bfe1-0b2a-4891-88c5-d4af752c0033",
                      "entryDate": "2026-09-15",
                      "title": "발표를 앞둔 날"
                    }
                  ]
                }
                """);
    }

    @Test
    void endActionIsReadFromRequestBody() {
        UpdateChatSessionRequest request = jsonMapper.readValue("{\"action\":\"END\"}", UpdateChatSessionRequest.class);

        assertThat(request.action()).isEqualTo(ChatSessionAction.END);
    }

    @Test
    void unknownActionIsRejected() {
        assertThatThrownBy(() -> jsonMapper.readValue("{\"action\":\"STOP\"}", UpdateChatSessionRequest.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void characterIdIsOptionalWhenSendingMessage() {
        SendMessageRequest request = jsonMapper.readValue("{\"content\":\"오늘 면접이 걱정돼요.\"}", SendMessageRequest.class);

        assertThat(request.characterId()).isNull();
        assertThat(request.hasContent()).isTrue();
    }

    private ChatMessageResponse userMessage(String content) {
        return new ChatMessageResponse(
                USER_MESSAGE_ID, 1, SenderRole.USER, null, content, NOW, null, List.of(), List.of());
    }

    private ChatMessageResponse assistantMessage(
            int sequenceNo,
            String content,
            List<ChatMessageResponse.DiaryReference> diaryReferences
    ) {
        return new ChatMessageResponse(
                ASSISTANT_MESSAGE_ID, sequenceNo, SenderRole.ASSISTANT, CHARACTER_ID, content, NOW, null,
                List.of(), diaryReferences);
    }

    private void assertJsonEquals(Object response, String expectedJson) {
        String actualJson = jsonMapper.writeValueAsString(response);

        assertThat(jsonMapper.readTree(actualJson)).isEqualTo(jsonMapper.readTree(expectedJson));
    }
}
