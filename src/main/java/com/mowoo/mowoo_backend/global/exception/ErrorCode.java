package com.mowoo.mowoo_backend.global.exception;

import org.springframework.http.HttpStatus;

// 도메인별 에러 코드 enum이 구현한다. enum 상수 이름이 응답의 error.code가 된다.
public interface ErrorCode {

    String name();

    HttpStatus getStatus();

    String getMessage();
}
