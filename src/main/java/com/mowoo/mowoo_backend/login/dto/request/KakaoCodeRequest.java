package com.mowoo.mowoo_backend.login.dto.request;

import jakarta.validation.constraints.NotBlank;

public record KakaoCodeRequest(@NotBlank String code) {}
