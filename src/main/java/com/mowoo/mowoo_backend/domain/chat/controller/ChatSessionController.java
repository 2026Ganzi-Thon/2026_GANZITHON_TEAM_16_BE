package com.mowoo.mowoo_backend.domain.chat.controller;

import com.mowoo.mowoo_backend.domain.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.request.UpdateChatSessionRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.DiarySuggestionResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.UpdateMessageResponse;
import com.mowoo.mowoo_backend.domain.chat.service.ChatService;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Tag(name = "chat", description = "대화 관련 api 입니다.")
@RestController
@RequestMapping("/api/v1/chat-sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatService chatService;
    private final JsonMapper jsonMapper;

    @Operation(summary = "대화 시작", description = "선택한 캐릭터로 대화를 시작합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse startSession(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartChatSessionRequest request
    ) {
        return chatService.startSession(userId(jwt), request);
    }

    @Operation(summary = "대화 보관함 조회", description = "대화 세션 목록을 최신순으로 조회합니다.")
    @GetMapping
    public CursorResponse<ChatSessionResponse> getSessions(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ParameterObject CursorRequest cursorRequest
    ) {
        return chatService.getSessions(userId(jwt), cursorRequest);
    }

    @Operation(summary = "대화 상세 조회", description = "대화 세션과 메시지 목록을 순번 순서로 조회합니다.")
    @GetMapping("/{sessionId}")
    public ChatSessionDetailResponse getSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @ParameterObject CursorRequest cursorRequest
    ) {
        return chatService.getSession(userId(jwt), sessionId, cursorRequest);
    }

    @Operation(summary = "대화 종료", description = "대화를 종료하고 보관함용 제목과 요약을 저장합니다.")
    @PatchMapping("/{sessionId}")
    public ChatSessionResponse updateSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpdateChatSessionRequest request
    ) {
        return switch (request.action()) {
            case END -> chatService.endSession(userId(jwt), sessionId);
        };
    }

    @Operation(summary = "대화 삭제", description = "세션·메시지·첨부를 삭제합니다. 이 대화로 만든 일기는 남습니다.")
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId
    ) {
        chatService.deleteSession(userId(jwt), sessionId);
    }

    @Operation(summary = "메시지 전송", description = "사용자 메시지와 AI 응답을 저장하고 대화 진행도를 반환합니다.")
    @PostMapping(value = "/{sessionId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SendMessageResponse sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @RequestBody SendMessageRequest request
    ) {
        return chatService.sendMessage(userId(jwt), sessionId, request, List.of());
    }

    // 사진을 함께 보낼 때는 payload 파트에 JSON 문자열, files 파트에 이미지를 반복해서 넣는다.
    @Operation(summary = "메시지 전송 (사진 포함)", description = "multipart의 payload에 JSON 문자열, files에 이미지를 넣어 보냅니다.")
    @PostMapping(value = "/{sessionId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SendMessageResponse sendMessageWithFiles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @RequestPart("payload") String payload,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        SendMessageRequest request = readPayload(payload, SendMessageRequest.class);
        return chatService.sendMessage(userId(jwt), sessionId, request, files == null ? List.of() : files);
    }

    @Operation(summary = "최근 메시지 수정", description = "가장 최근의 사용자 메시지를 수정하고 그 응답을 갱신합니다.")
    @PatchMapping("/{sessionId}/messages/{messageId}")
    public UpdateMessageResponse updateMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @PathVariable UUID messageId,
            @Valid @RequestBody UpdateMessageRequest request
    ) {
        return chatService.updateMessage(userId(jwt), sessionId, messageId, request);
    }

    @Operation(summary = "과거 일기 후보 조회", description = "현재 메시지와 관련된 본인의 저장 일기 후보를 조회합니다.")
    @GetMapping("/{sessionId}/diary-suggestions")
    public ListResponse<DiarySuggestionResponse> getDiarySuggestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @RequestParam UUID messageId
    ) {
        return chatService.getDiarySuggestions(userId(jwt), sessionId, messageId);
    }

    @Operation(summary = "과거 일기 연결 동의", description = "선택한 과거 일기를 연결하고 비교 답변을 저장합니다.")
    @PostMapping("/{sessionId}/diary-references")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateDiaryReferenceResponse createDiaryReference(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreateDiaryReferenceRequest request
    ) {
        return chatService.createDiaryReference(userId(jwt), sessionId, request);
    }

    // JWT의 sub에 users.id가 들어 있다. 공통 로그인 사용자 파라미터가 생기면 그것으로 바꾼다.
    private UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private <T> T readPayload(String payload, Class<T> type) {
        try {
            return jsonMapper.readValue(payload, type);
        } catch (JacksonException e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
    }
}
