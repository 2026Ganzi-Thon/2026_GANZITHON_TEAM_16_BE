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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleClientTest {
    private HttpServer server;
    private GoogleClient client;
    private final AtomicReference<String> profile = new AtomicReference<>();
    private final AtomicReference<String> tokenBody = new AtomicReference<>("{\"access_token\":\"provider-token\"}");
    private final AtomicReference<String> receivedForm = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger tokenStatus = new AtomicInteger(200);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            receivedForm.set(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            send(exchange, tokenBody.get(), tokenStatus.get());
        });
        server.createContext("/profile", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, profile.get());
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new GoogleClient("google-id", "google-secret", "https://mowoo.test/google",
                base + "/token", base + "/profile");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void googleExchangesCodeAndMapsVerifiedProfile() {
        profile.set("""
                {"sub":"google-sub","name":"구글 사용자","email":"google@example.test",
                 "email_verified":true,"picture":"https://images.example.test/google.png"}
                """);
        SocialUser user = client.getUser("code +&");
        assertThat(user.provider()).isEqualTo(Provider.GOOGLE);
        assertThat(user.subject()).isEqualTo("google-sub");
        assertThat(user.nickname()).isEqualTo("구글 사용자");
        assertThat(user.email()).isEqualTo("google@example.test");
        assertThat(user.profileImageUrl()).isEqualTo("https://images.example.test/google.png");
        assertThat(receivedForm.get()).contains("grant_type=authorization_code", "code=code +&",
                "client_id=google-id", "client_secret=google-secret", "redirect_uri=https://mowoo.test/google");
        assertThat(authorization.get()).isEqualTo("Bearer provider-token");
    }

    @Test
    void googleSkipsUnverifiedEmailAndUnsafePhoto() {
        profile.set("""
                {"sub":"google-sub","name":"구글 사용자","email":"google@example.test",
                 "email_verified":false,"picture":"http://images.example.test/google.png"}
                """);
        SocialUser user = client.getUser("code");
        assertThat(user.email()).isNull();
        assertThat(user.profileImageUrl()).isNull();
    }

    @Test
    void providerErrorIsConvertedAndMissingSubjectIsRejected() {
        tokenBody.set("{\"error\":\"invalid_grant\"}");
        assertError(() -> client.getUser("invalid"), AuthErrorCode.SOCIAL_CODE_INVALID);
        tokenBody.set("{\"access_token\":\"provider-token\"}");
        profile.set("{\"name\":\"사용자\"}");
        assertError(() -> client.getUser("code"), AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    void invalidClientIsNotReportedAsAnInvalidAuthorizationCode() {
        tokenStatus.set(400);
        tokenBody.set("{\"error\":\"invalid_client\",\"error_description\":\"client configuration is wrong\"}");
        assertError(() -> client.getUser("code"), AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
    }

    @Test
    void expiredOrReusedCodeIsReportedAsInvalidGrant() {
        tokenStatus.set(400);
        tokenBody.set("{\"error\":\"invalid_grant\"}");
        assertError(() -> client.getUser("code"), AuthErrorCode.SOCIAL_CODE_INVALID);
    }

    private void assertError(Runnable request, AuthErrorCode code) {
        assertThatThrownBy(request::run).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode()).isEqualTo(code);
    }

    private void send(HttpExchange exchange, String body) throws IOException {
        send(exchange, body, 200);
    }

    private void send(HttpExchange exchange, String body, int status) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
