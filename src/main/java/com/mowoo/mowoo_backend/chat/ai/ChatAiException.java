package com.mowoo.mowoo_backend.chat.ai;

// AI 응답 생성 실패. 시간 초과, 외부 API 오류, 빈 응답 등을 감싼다.
public class ChatAiException extends RuntimeException {

    public ChatAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
