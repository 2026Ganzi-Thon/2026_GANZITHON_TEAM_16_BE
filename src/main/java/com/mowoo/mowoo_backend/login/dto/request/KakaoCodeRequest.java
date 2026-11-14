package com.mowoo.mowoo_backend.login.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoCodeRequest(
        @Schema(description = "카카오 콜백에서 받은 일회용 인가 코드", example = "kakao-authorization-code")
        @NotBlank String code
) {}
