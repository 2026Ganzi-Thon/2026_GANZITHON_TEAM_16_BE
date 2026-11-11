package com.mowoo.mowoo_backend.ai.solar;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SolarChatClient {
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private final RestClient http;
    private final SolarProperties settings;
    private final JsonMapper json;

    public SolarChatClient(@Qualifier("solarRestClient") RestClient http, SolarProperties settings, JsonMapper json) {
        this.http = http;
        this.settings = settings;
        this.json = json;
    }

    public record Prompt(String schemaName, String instructions, String input, Map<String, Object> schema) { }
    public record Usage(long promptTokens, long completionTokens, long totalTokens) { }
    public record Result(String content, Usage usage) { }

    public Result complete(Prompt prompt) {
        if (!settings.isEnabled() || settings.getApiKey() == null || settings.getApiKey().isBlank()) {
            throw new SolarApiException(SolarApiException.Kind.DISABLED);
        }
        if (prompt == null || prompt.instructions() == null || prompt.input() == null
                || prompt.instructions().length() + prompt.input().length() > settings.getMaxInputChars()) {
            throw new SolarApiException(SolarApiException.Kind.CONFIGURATION);
        }
        Map<String, Object> body = Map.of(
                "model", settings.getModel(), "stream", false, "reasoning_effort", "none",
                "max_tokens", settings.getMaxOutputTokens(),
                "messages", List.of(Map.of("role", "system", "content", prompt.instructions()),
                        Map.of("role", "user", "content", prompt.input())),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", prompt.schemaName(), "strict", true, "schema", prompt.schema())));
        try {
            return http.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(settings.getApiKey())).body(body)
                    .exchange((request, response) -> {
                        byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (bytes.length > MAX_RESPONSE_BYTES) throw invalid();
                        int status = response.getStatusCode().value();
                        if (status != 200) throw classify(status, bytes);
                        return parse(bytes);
                    });
        } catch (SolarApiException e) {
            throw e;
        } catch (RestClientException e) {
            // 타임아웃·연결 실패의 외부 예외 메시지/응답은 로그나 사용자 응답으로 전달하지 않는다.
            throw new SolarApiException(SolarApiException.Kind.CONNECTION);
        }
    }

    private Result parse(byte[] bytes) {
        try {
            JsonNode root = json.readTree(bytes);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) throw invalid();
            JsonNode choice = choices.get(0);
            if (!"stop".equals(choice.path("finish_reason").asString())) throw invalid();
            JsonNode content = choice.path("message").path("content");
            if (!content.isString() || content.asString().isBlank()) throw invalid();
            if (!json.readTree(content.asString()).isObject()) throw invalid();
            JsonNode usage = root.path("usage");
            Usage counts = null;
            if (usage.path("prompt_tokens").isIntegralNumber()
                    && usage.path("completion_tokens").isIntegralNumber() && usage.path("total_tokens").isIntegralNumber()) {
                counts = new Usage(usage.path("prompt_tokens").asLong(),
                        usage.path("completion_tokens").asLong(), usage.path("total_tokens").asLong());
                log.info("Solar usage model={} inputTokens={} outputTokens={} totalTokens={}",
                        settings.getModel(), counts.promptTokens(), counts.completionTokens(), counts.totalTokens());
            }
            return new Result(content.asString(), counts);
        } catch (SolarApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private SolarApiException classify(int status, byte[] bytes) {
        String code = "";
        try { code = json.readTree(bytes).path("error").path("code").asString(); }
        catch (RuntimeException ignored) { /* 오류 본문이 없는 응답도 상태 코드로 분류한다. */ }
        if ("api_key_is_blocked".equals(code) || "api_key_is_not_allowed".equals(code)
                || "usage_limit_exceeded".equals(code)) return new SolarApiException(SolarApiException.Kind.QUOTA);
        if (status == 401 || status == 403) return new SolarApiException(SolarApiException.Kind.AUTHENTICATION);
        if (status == 429) return new SolarApiException(SolarApiException.Kind.RATE_LIMIT);
        if (status >= 500) return new SolarApiException(SolarApiException.Kind.SERVER);
        return new SolarApiException(SolarApiException.Kind.CONFIGURATION);
    }

    private SolarApiException invalid() {
        return new SolarApiException(SolarApiException.Kind.INVALID_RESPONSE);
    }
}
