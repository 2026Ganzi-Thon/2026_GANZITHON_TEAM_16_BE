package com.mowoo.mowoo_backend.global.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return toResponse(e.getErrorCode());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e) {
        return toResponse(CommonErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        return toResponse(CommonErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("처리하지 못한 예외가 발생했습니다.", e);
        return toResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    // 검증 실패, JSON 파싱 실패, 없는 경로처럼 Spring MVC가 처리하는 예외도 공통 에러 형식으로 바꾼다.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null) {
            return null;
        }

        BaseCode errorCode = toErrorCode(statusCode);
        return ResponseEntity.status(errorCode.getStatus())
                .headers(response.getHeaders())
                .body(ErrorResponse.of(errorCode));
    }

    private BaseCode toErrorCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> CommonErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> statusCode.is4xxClientError()
                    ? CommonErrorCode.VALIDATION_ERROR
                    : CommonErrorCode.INTERNAL_ERROR;
        };
    }

    private ResponseEntity<ErrorResponse> toResponse(BaseCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }
}
