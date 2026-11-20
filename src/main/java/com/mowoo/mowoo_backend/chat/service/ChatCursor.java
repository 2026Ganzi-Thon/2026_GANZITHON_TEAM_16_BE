package com.mowoo.mowoo_backend.chat.service;

import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

// 대화 목록의 커서를 클라이언트가 내용을 해석하지 않는 문자열로 만들고 읽는다.
// 읽을 수 없는 커서는 VALIDATION_ERROR로 응답한다.
final class ChatCursor {

    private static final String SEPARATOR = "|";

    private ChatCursor() {
    }

    // 보관함: startedAt이 같으면 id로 순서를 정한다.
    record SessionCursor(Instant startedAt, UUID id) {
    }

    static String encodeSession(Instant startedAt, UUID id) {
        return encode(startedAt + SEPARATOR + id);
    }

    static SessionCursor decodeSession(String cursor) {
        try {
            String[] parts = decode(cursor).split("\\" + SEPARATOR, -1);
            if (parts.length != 2) {
                throw invalid();
            }
            return new SessionCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    // 대화 상세: 이번 페이지에서 가장 오래된 메시지의 순번. 다음 페이지는 그보다 앞선 메시지다.
    static String encodeMessage(int sequenceNo) {
        return encode(String.valueOf(sequenceNo));
    }

    static int decodeMessage(String cursor) {
        try {
            return Integer.parseInt(decode(cursor));
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String cursor) {
        return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    }

    private static BusinessException invalid() {
        return new BusinessException(CommonErrorCode.VALIDATION_ERROR);
    }
}
