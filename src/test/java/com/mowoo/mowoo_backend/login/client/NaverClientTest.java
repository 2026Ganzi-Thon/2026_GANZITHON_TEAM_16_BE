package com.mowoo.mowoo_backend.login.client;

import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity.Provider;
import com.mowoo.mowoo_backend.login.exception.AuthErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NaverClientTest {
    private HttpServer server;
    private NaverClient client;
    private final AtomicReference<String> profile = new AtomicReference<>();
    private final AtomicReference<String> tokenBody = new AtomicReference<>("{\"access_token\":\"provider-token\"}");
    private final AtomicReference<String> receivedForm = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            receivedForm.set(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            send(exchange, tokenBody.get());
        });
        server.createContext("/profile", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, profile.get());
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new NaverClient("naver-id", "naver-secret", "https://mowoo.test/naver",
                base + "/token", base + "/profile");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void naverForwardsCallbackStateAndMapsProfile() {
        profile.set("""
                {"resultcode":"00","response":{"id":"naver-sub","name":"네이버 사용자","nickname":"별명",
                 "email":"naver@example.test","profile_image":"https://images.example.test/naver.png"}}
                """);
        SocialUser user = client.getUser("code", "callback-state");
        assertThat(user.provider()).isEqualTo(Provider.NAVER);
        assertThat(user.subject()).isEqualTo("naver-sub");
        assertThat(user.nickname()).isEqualTo("네이버 사용자");
        assertThat(user.email()).isEqualTo("naver@example.test");
        assertThat(user.profileImageUrl()).isEqualTo("https://images.example.test/naver.png");
        assertThat(receivedForm.get()).contains("state=callback-state", "client_id=naver-id");
    }

    @Test
    void naverRejectsMissingStateBeforeContactingProvider() {
        assertError(() -> client.getUser("code", null), AuthErrorCode.SOCIAL_CODE_INVALID);
        assertThat(receivedForm.get()).isNull();
    }

    @Test
    void providerErrorIsConvertedAndMissingSubjectIsRejected() {
        tokenBody.set("{\"error\":\"invalid_grant\"}");
        assertError(() -> client.getUser("invalid", "state"), AuthErrorCode.SOCIAL_CODE_INVALID);
        tokenBody.set("{\"access_token\":\"provider-token\"}");
        profile.set("{\"resultcode\":\"00\",\"response\":{}}");
        assertError(() -> client.getUser("code", "state"), AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    void usesNicknameWhenTheUserDidNotProvideAName() {
        profile.set("{\"resultcode\":\"00\",\"response\":{\"id\":\"naver-sub\",\"nickname\":\"별명\"}}");
        assertThat(client.getUser("code", "state").nickname()).isEqualTo("별명");
    }

    private void assertError(Runnable request, AuthErrorCode code) {
        assertThatThrownBy(request::run).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode()).isEqualTo(code);
    }

    private void send(HttpExchange exchange, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
