package com.mowoo.mowoo_backend.chat.ai;

import com.mowoo.mowoo_backend.ai.solar.SolarApiException;

// AI 응답 생성 실패. 시간 초과, 외부 API 오류, 빈 응답 등을 감싼다.
public class ChatAiException extends RuntimeException {

    public ChatAiException(String message, Throwable cause) {
        super(message, cause);
    }

    // 로그용 실패 원인. 외부 API 오류는 그 종류를, 형식이나 기준에 맞지 않는 응답은 INVALID_OUTPUT을 돌려준다.
    public String reason() {
        return getCause() instanceof SolarApiException e ? e.getKind().name() : "INVALID_OUTPUT";
    }
}
