package com.mowoo.mowoo_backend.ai;

// 도메인은 제공사 응답/인증정보를 모르고 생성 실패만 처리한다.
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() { super("AI generation unavailable"); }
}
