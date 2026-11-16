package com.mowoo.mowoo_backend.login.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record NaverCodeRequest(
        @Schema(description = "네이버 콜백에서 받은 일회용 인가 코드", example = "naver-authorization-code")
        @NotBlank String code,
        @Schema(description = "로그인 시작 때 생성하고 콜백에서 일치 여부를 확인한 state. 네이버 토큰 교환 필수값", example = "random-state-from-login-start")
        @NotBlank @Size(max = 1024) String state
) {
}
