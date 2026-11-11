package com.mowoo.mowoo_backend.ai.solar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SolarChatClientTest {
    private SolarProperties settings;
    private SolarChatClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        settings = new SolarProperties();
        settings.setEnabled(true);
        settings.setApiKey("unused-unit-test-key");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.upstage.ai/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SolarChatClient(builder.build(), settings, JsonMapper.builder().build());
    }

    @Test
    void sendsStructuredRequestAndReadsContentAndUsage() {
        server.expect(requestTo("https://api.upstage.ai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer unused-unit-test-key"))
                .andExpect(jsonPath("$.model").value("solar-pro4"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.reasoning_effort").value("none"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"ok\\":true}","reasoning":"do not persist"},
                        "finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}}
                        """, MediaType.APPLICATION_JSON));
        var result = client.complete(prompt());
        assertThat(result.content()).isEqualTo("{\"ok\":true}");
        assertThat(result.usage().promptTokens()).isEqualTo(12);
        assertThat(result.usage().completionTokens()).isEqualTo(8);
        server.verify();
    }

    @Test
    void disabledOrMissingKeyMakesNoHttpRequest() {
        settings.setEnabled(false);
        assertThatThrownBy(() -> client.complete(prompt())).isInstanceOf(SolarApiException.class);
        settings.setEnabled(true);
        settings.setApiKey("");
        assertThatThrownBy(() -> client.complete(prompt())).isInstanceOf(SolarApiException.class);
        server.verify();
    }

    @Test
    void providerFailureDoesNotExposeResponseBodyOrRetainRawCause() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"api_key_is_blocked\",\"message\":\"private-upstream-text\"}}"));
        assertThatThrownBy(() -> client.complete(prompt())).isInstanceOf(SolarApiException.class)
                .hasMessageNotContaining("private-upstream-text").hasNoCause();
        server.verify();
    }

    @Test
    void rateLimitAndQuotaFailuresDoNotAutomaticallyRetry() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"usage_limit_exceeded\"}}"));
        assertThatThrownBy(() -> client.complete(prompt())).isInstanceOf(SolarApiException.class);
        server.verify();
    }

    @Test
    void rejectsTruncatedOutputAndEmptyChoices() {
        server.expect(anything()).andRespond(withSuccess("""
                {"choices":[{"message":{"content":"{}"},"finish_reason":"length"}]}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete(prompt())).isInstanceOf(SolarApiException.class);
        server.reset();
        server.expect(anything()).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete(prompt())).isInstanceOf(SolarApiException.class);
        server.verify();
    }

    @Test
    void connectionFailureIsSanitized() {
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("private network details")));
        assertThatThrownBy(() -> client.complete(prompt())).isInstanceOf(SolarApiException.class)
                .hasMessageNotContaining("private network details").hasNoCause();
    }

    @Test
    void malformedProviderBodiesAreSanitized() {
        for (String body : new String[]{"not JSON", "null", "{}", "{\"choices\":null}"}) {
            server.reset();
            server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.complete(prompt())).isInstanceOf(SolarApiException.class).hasNoCause();
            server.verify();
        }
    }

    @Test
    void httpErrorsMapToSafeCategoriesWithoutRetries() {
        int[] statuses = {400, 401, 403, 429, 500, 503};
        SolarApiException.Kind[] kinds = {SolarApiException.Kind.CONFIGURATION, SolarApiException.Kind.AUTHENTICATION,
                SolarApiException.Kind.AUTHENTICATION, SolarApiException.Kind.RATE_LIMIT,
                SolarApiException.Kind.SERVER, SolarApiException.Kind.SERVER};
        for (int i = 0; i < statuses.length; i++) {
            server.reset();
            server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(statuses[i]))
                    .body("{\"error\":{\"code\":\"unclassified\"}}"));
            SolarApiException.Kind expected = kinds[i];
            assertThatThrownBy(() -> client.complete(prompt())).isInstanceOfSatisfying(SolarApiException.class,
                    error -> assertThat(error.getKind()).isEqualTo(expected));
            server.verify();
        }
    }

    private SolarChatClient.Prompt prompt() {
        return new SolarChatClient.Prompt("test_result", "Return the supplied JSON schema.", "synthetic data",
                Map.of("type", "object", "properties", Map.of("ok", Map.of("type", "boolean")),
                        "required", java.util.List.of("ok"), "additionalProperties", false));
    }
}
