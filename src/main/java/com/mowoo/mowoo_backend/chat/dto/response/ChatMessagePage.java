package com.mowoo.mowoo_backend.chat.dto.response;

import java.util.List;

// 대화 상세 안의 메시지 커서 페이지. 응답 안에 들어가므로 isSuccess·code·message를 담지 않는다.
// 마지막 페이지면 nextCursor는 null이다.
public record ChatMessagePage(List<ChatMessageResponse> items, String nextCursor) {
}
