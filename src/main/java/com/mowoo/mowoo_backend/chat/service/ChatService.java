package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionDetailResponse;
import com.mowoo.mowoo_backend.chat.dto.response.ChatSessionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.CreateDiaryReferenceResponse;
import com.mowoo.mowoo_backend.chat.dto.response.DiarySuggestionResponse;
import com.mowoo.mowoo_backend.chat.dto.response.SendMessageResponse;
import com.mowoo.mowoo_backend.chat.dto.response.UpdateMessageResponse;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import com.mowoo.mowoo_backend.global.response.CursorResponse;
import com.mowoo.mowoo_backend.global.response.ListResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// 각 기능은 대화 엔티티를 사용하도록 차례로 구현한다. 구현 전 기능은 UnsupportedOperationException을 던진다.
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
        return new UnsupportedOperationException("구현 예정");
    }
}
