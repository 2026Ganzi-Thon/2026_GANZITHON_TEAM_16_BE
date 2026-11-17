package com.mowoo.mowoo_backend.chat.ai;

import com.mowoo.mowoo_backend.ai.solar.SolarApiException;
import tools.jackson.core.JacksonException;

import java.util.function.Function;

// 응답 형식이 깨졌거나 기준을 크게 벗어난 경우에만 한 번 더 요청한다.
// 시간 초과·인증·사용량 한도 오류는 다시 요청해도 결과가 같거나 대기만 길어지므로 바로 실패시킨다.
final class ChatAiRetry {
    private static final int MAX_ATTEMPTS = 2;

    // 다시 요청할 만한 응답이면 attempt가 던진다. 응답 내용은 담지 않는다.
    static final class Rejected extends RuntimeException {
        Rejected() {
            super(null, null, false, false);
        }
    }

    private ChatAiRetry() {
    }

    // attempt는 마지막 시도인지를 받는다. 마지막 시도에서는 길이 같은 느슨한 기준을 건너뛰고 응답을 받아들인다.
    static <T> T call(Function<Boolean, T> attempt) {
        for (int i = 1; ; i++) {
            boolean last = i == MAX_ATTEMPTS;
            try {
                return attempt.apply(last);
            } catch (Rejected | JacksonException e) {
                if (last) throw new Rejected();
            } catch (SolarApiException e) {
                if (last || e.getKind() != SolarApiException.Kind.INVALID_RESPONSE) throw e;
            }
        }
    }
}
