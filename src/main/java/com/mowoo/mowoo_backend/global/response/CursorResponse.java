package com.mowoo.mowoo_backend.global.response;

import java.util.List;

// 커서 페이지 목록 응답: {"items": [...], "nextCursor": "..."}. 마지막 페이지면 nextCursor는 null이다.
public record CursorResponse<T>(List<T> items, String nextCursor) {
}
