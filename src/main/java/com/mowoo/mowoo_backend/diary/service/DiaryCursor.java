package com.mowoo.mowoo_backend.diary.service;

import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

final class DiaryCursor {

    private DiaryCursor() {
    }

    record Position(LocalDate entryDate, UUID id) {
    }

    static String encode(LocalDate date, UUID id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((date + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    static Position decode(String value) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
                    .split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            return new Position(LocalDate.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
    }
}
