package com.mowoo.mowoo_backend.domain.chat.exception;

import com.mowoo.mowoo_backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화를 찾을 수 없어요."),
    SESSION_CLOSED(HttpStatus.CONFLICT, "이미 종료된 대화예요."),
    MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "메시지 내용이나 사진을 넣어 주세요."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없어요."),
    MESSAGE_NOT_EDITABLE(HttpStatus.CONFLICT, "가장 최근에 보낸 메시지만 수정할 수 있어요."),
    PAST_DIARY_DISABLED(HttpStatus.FORBIDDEN, "과거 일기 활용 설정이 꺼져 있어요.");

    private final HttpStatus status;
    private final String message;
}
