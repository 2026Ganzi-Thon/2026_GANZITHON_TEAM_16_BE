package com.mowoo.mowoo_backend.attachment.web;

import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.global.exception.CommonErrorCode;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class MultipartPayload {
    private final JsonMapper json;
    private final Validator validator;
    // FormData.append("payload", JSON.stringify(...))의 text/plain과 JSON 파트를 모두 지원한다.
    public <T> T read(String payload, Class<T> type) {
        try {
            T request = json.readValue(payload, type);
            if (request == null || !validator.validate(request).isEmpty()) throw invalid();
            return request;
        } catch (JacksonException | IllegalArgumentException e) { throw invalid(); }
    }
    private BusinessException invalid() { return new BusinessException(CommonErrorCode.VALIDATION_ERROR); }
}
