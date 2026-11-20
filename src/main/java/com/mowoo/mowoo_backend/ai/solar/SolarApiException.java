package com.mowoo.mowoo_backend.ai.solar;

// 외부 응답 본문, 프롬프트, 인증 헤더 또는 원본 예외를 보관하지 않는다.
public class SolarApiException extends RuntimeException {
    public enum Kind { DISABLED, CONFIGURATION, AUTHENTICATION, QUOTA, RATE_LIMIT, SERVER, CONNECTION, INVALID_RESPONSE }
    private final Kind kind;

    public SolarApiException(Kind kind) {
        super("Solar API request failed: " + kind.name());
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}
