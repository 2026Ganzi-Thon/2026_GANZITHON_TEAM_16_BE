package com.mowoo.mowoo_backend.domain.chat.service;

import com.mowoo.mowoo_backend.domain.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.domain.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.DiarySuggestionResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.domain.chat.dto.response.UpdateMessageResponse;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// 대화 엔티티는 users·ai_characters 엔티티가 develop에 들어온 뒤 만든다. 그 전까지 각 기능은 구현 전 상태다.
@Service
public class ChatService {

    public ChatSessionResponse startSession(UUID userId, StartChatSessionRequest request) {
        throw notImplemented();
    }

    public CursorResponse<ChatSessionResponse> getSessions(UUID userId, CursorRequest cursorRequest) {
        throw notImplemented();
    }

    public ChatSessionDetailResponse getSession(UUID userId, UUID sessionId, CursorRequest cursorRequest) {
        throw notImplemented();
    }

    public ChatSessionResponse endSession(UUID userId, UUID sessionId) {
        throw notImplemented();
    }

    public void deleteSession(UUID userId, UUID sessionId) {
        throw notImplemented();
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

    private UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException("대화 엔티티 연결 후 구현 예정");
    }
}
