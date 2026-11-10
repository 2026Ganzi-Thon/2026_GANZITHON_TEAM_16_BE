package com.mowoo.mowoo_backend.global.response;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonSuccessCode implements BaseCode {

    SUCCESS(HttpStatus.OK, "요청이 처리됐어요."),
    HEALTH_CHECK_SUCCESS(HttpStatus.OK, "OK"),
    LOGIN_SUCCESS(HttpStatus.OK, "로그인에 성공했어요."),
    TOKEN_REISSUE_SUCCESS(HttpStatus.OK, "토큰을 재발급했어요."),
    LOGOUT_SUCCESS(HttpStatus.OK, "로그아웃했어요."),
    USER_PROFILE_READ_SUCCESS(HttpStatus.OK, "프로필 조회가 완료됐어요."),
    USER_PROFILE_UPDATE_SUCCESS(HttpStatus.OK, "프로필 수정이 완료됐어요."),
    CHAT_SESSION_START_SUCCESS(HttpStatus.CREATED, "대화를 시작했어요."),
    CHAT_SESSION_LIST_READ_SUCCESS(HttpStatus.OK, "대화 보관함 조회가 완료됐어요."),
    CHAT_SESSION_READ_SUCCESS(HttpStatus.OK, "대화 조회가 완료됐어요."),
    CHAT_SESSION_END_SUCCESS(HttpStatus.OK, "대화를 종료했어요."),
    CHAT_SESSION_DELETE_SUCCESS(HttpStatus.OK, "대화를 삭제했어요."),
    CHAT_MESSAGE_SEND_SUCCESS(HttpStatus.CREATED, "메시지를 보냈어요."),
    CHAT_MESSAGE_UPDATE_SUCCESS(HttpStatus.OK, "메시지를 수정했어요."),
    DIARY_SUGGESTION_READ_SUCCESS(HttpStatus.OK, "과거 일기 후보 조회가 완료됐어요."),
    DIARY_REFERENCE_CREATE_SUCCESS(HttpStatus.CREATED, "과거 일기를 연결했어요.");

    private final HttpStatus status;
    private final String message;
}
