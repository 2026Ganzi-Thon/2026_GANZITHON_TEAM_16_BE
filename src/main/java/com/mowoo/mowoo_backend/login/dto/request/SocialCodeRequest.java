package com.mowoo.mowoo_backend.login.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record SocialCodeRequest(
        @Schema(description = "구글 콜백에서 받은 일회용 인가 코드. URL 디코딩된 원문을 전달합니다.", example = "4/example-authorization-code")
        @NotBlank String code
) {
}
