package com.mowoo.mowoo_backend.chat.controller;

import com.mowoo.mowoo_backend.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionItem;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.chat.dto.response.DiarySuggestionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.UpdateMessageResponse;
import com.mowoo.mowoo_backend.chat.service.ChatService;
import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import com.mowoo.mowoo_backend.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "chat", description = "대화 관련 api 입니다.")
@RestController
@RequestMapping("/api/v1/chat-sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatService chatService;

    @Operation(summary = "대화 시작", description = "보유한 캐릭터의 characterId로 대화를 생성합니다. 미보유 캐릭터는 403 CHARACTER_NOT_OWNED를 반환합니다. 성공 시 201입니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse startSession(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartChatSessionRequest request
    ) {
        return chatService.startSession(userId(jwt), request);
    }

    @Operation(summary = "대화 보관함 조회", description = "본인의 대화를 최신순으로 조회합니다. limit은 기본 20·최대 100이며 다음 페이지는 nextCursor를 cursor로 전달합니다.")
    @GetMapping
    public CursorResponse<ChatSessionItem> getSessions(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ParameterObject CursorRequest cursorRequest
    ) {
        return chatService.getSessions(userId(jwt), cursorRequest);
    }

    @Operation(summary = "대화 상세 조회", description = "본인 대화와 메시지를 순번순으로 조회합니다. 메시지 목록에는 limit(기본 20·최대 100)과 cursor를 사용합니다. 다른 사용자의 대화는 404입니다.")
    @GetMapping("/{sessionId}")
    public ChatSessionDetailResponse getSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @ParameterObject CursorRequest cursorRequest
    ) {
        return chatService.getSession(userId(jwt), sessionId, cursorRequest);
    }

    @Operation(summary = "대화 종료", description = "action에 END를 전달하여 본인 대화를 종료하고 제목·요약을 생성합니다. 요약 생성 실패 시에도 종료는 성공하며 제목·요약이 null일 수 있습니다.")
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

    @Operation(summary = "대화 삭제", description = "본인 대화, 메시지와 메시지의 일기 참조를 삭제합니다. 해당 대화로 생성한 일기는 보존하고 원본 대화 연결을 비웁니다.")
    @DeleteMapping("/{sessionId}")
    public SuccessResponse deleteSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId
    ) {
        chatService.deleteSession(userId(jwt), sessionId);
        return SuccessResponse.of(CommonSuccessCode.CHAT_SESSION_DELETE_SUCCESS);
    }

    @Operation(summary = "메시지 전송", description = "진행 중인 본인 대화에 사용자 메시지와 AI 답변을 한 왕복으로 저장합니다. AI 생성 실패 시 사용자 메시지도 저장하지 않습니다. completedTurns와 canGenerateDiary를 반환하며 성공 시 201입니다.")
    @PostMapping("/{sessionId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public SendMessageResponse sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @RequestBody SendMessageRequest request
    ) {
        return chatService.sendMessage(userId(jwt), sessionId, request);
    }

    @Operation(summary = "최근 메시지 수정", description = "진행 중인 대화의 가장 최근 사용자 메시지 내용과 수정 시각만 변경합니다. AI 답변은 다시 만들지 않습니다. 과거 일기 비교 답변과 일기 참조도 유지됩니다. 수정 내용은 다음 AI 대화의 기록에 반영됩니다. 이전 사용자 메시지·AI 메시지·종료된 대화는 수정할 수 없으며 409를 반환합니다.")
    @PatchMapping("/{sessionId}/messages/{messageId}")
    public UpdateMessageResponse updateMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @PathVariable UUID messageId,
            @Valid @RequestBody UpdateMessageRequest request
    ) {
        return chatService.updateMessage(userId(jwt), sessionId, messageId, request);
    }

    @Operation(summary = "과거 일기 후보 조회", description = "과거 일기 사용 설정이 켜져 있을 때 messageId와 관련된 본인의 저장 일기 후보를 반환합니다. 이 조회만으로 일기를 연결하거나 비교 답변을 생성하지 않습니다.")
    @GetMapping("/{sessionId}/diary-suggestions")
    public ListResponse<DiarySuggestionResponse> getDiarySuggestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @RequestParam UUID messageId
    ) {
        return chatService.getDiarySuggestions(userId(jwt), sessionId, messageId);
    }

    @Operation(summary = "과거 일기 연결 동의", description = "선택한 본인 과거 일기를 메시지에 연결하고 AI 비교 답변을 저장합니다. 답변과 참조 관계를 함께 생성하며 성공 시 201입니다.")
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
}
