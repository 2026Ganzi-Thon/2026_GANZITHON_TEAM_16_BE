package com.mowoo.mowoo_backend.diary.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DiaryErrorCode implements BaseCode {

    DIARY_NOT_FOUND(HttpStatus.NOT_FOUND, "일기를 찾을 수 없어요."),
    MIN_CHAT_TURNS(HttpStatus.CONFLICT, "대화를 5왕복 이상 나눈 뒤 일기를 만들 수 있어요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 응답을 잠시 후 다시 요청해 주세요."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 날짜 범위를 확인해 주세요.");

    private final HttpStatus status;
    private final String message;
}
