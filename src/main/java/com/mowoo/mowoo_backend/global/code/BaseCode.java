package com.mowoo.mowoo_backend.global.code;

import org.springframework.http.HttpStatus;

public interface BaseCode {

    String name();

    HttpStatus getStatus();

    String getMessage();
}
