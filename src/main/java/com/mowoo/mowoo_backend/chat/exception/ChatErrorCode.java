package com.mowoo.mowoo_backend.chat.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseCode {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화를 찾을 수 없어요."),
    SESSION_CLOSED(HttpStatus.CONFLICT, "이미 종료된 대화예요."),
    CHARACTER_NOT_OWNED(HttpStatus.FORBIDDEN, "보유하지 않은 캐릭터예요."),
    MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "메시지 내용을 넣어 주세요."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지를 찾을 수 없어요."),
    MESSAGE_NOT_EDITABLE(HttpStatus.CONFLICT, "가장 최근에 보낸 메시지만 수정할 수 있어요."),
    PAST_DIARY_DISABLED(HttpStatus.FORBIDDEN, "과거 일기 활용 설정이 꺼져 있어요."),
    // 일기·감정 API도 같은 코드를 쓴다. 공통 에러 코드로 옮기기로 하면 CommonErrorCode로 옮긴다.
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 답변을 만들 수 없어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
