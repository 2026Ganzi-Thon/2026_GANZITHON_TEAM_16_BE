package com.mowoo.mowoo_backend.global.response;

import java.util.List;

// 페이지가 없는 목록 응답: {"items": [...]}
public record ListResponse<T>(List<T> items) {
}
