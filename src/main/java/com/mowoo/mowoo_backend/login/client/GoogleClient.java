package com.mowoo.mowoo_backend.login.client;

import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity.Provider;
import com.mowoo.mowoo_backend.login.exception.AuthErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
public class GoogleClient {
    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUri;
    private final String userInfoUri;

    public GoogleClient(
            @Value("${google.client-id:}") String clientId,
            @Value("${google.client-secret:}") String clientSecret,
            @Value("${google.redirect-uri:}") String redirectUri,
            @Value("${google.token-uri}") String tokenUri,
            @Value("${google.user-info-uri}") String userInfoUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        http = RestClient.builder().requestFactory(factory).build();


        log.info("Google redirectUri={}", redirectUri);
        log.info("Google tokenUri={}", tokenUri);
        log.info("Google clientId configured={}", !clientId.isBlank());
        log.info("Google clientSecret configured={}", !clientSecret.isBlank());
    }

    public SocialUser getUser(String code) {
        var response = userInfo(exchange(code));
        String email = response.path("email_verified").asBoolean(false)
                ? response.path("email").asString(null) : null;
        return profile(Provider.GOOGLE, response.path("sub").asString(null),
                response.path("name").asString(""), email, response.path("picture").asString(null));
    }

    private String exchange(String code) {
        if (clientId.isBlank() || redirectUri.isBlank()) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        JsonNode response;
        try {
            log.info(
                    "Google code check: length={}, startsWith4Slash={}, containsSpace={}, containsPercent={}",
                    code == null ? 0 : code.length(),
                    code != null && code.startsWith("4/"),
                    code != null && code.contains(" "),
                    code != null && code.contains("%")
            );
            response = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new BusinessException(tokenExchangeError(exception));
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        if (response == null) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        String error = response.path("error").asString("");
        if ("invalid_grant".equals(error) || "invalid_request".equals(error)) {
            throw new BusinessException(AuthErrorCode.SOCIAL_CODE_INVALID);
        }
        String token = response.path("access_token").asString("");
        if (!error.isBlank() || token.isBlank()) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        return token;
    }

    private AuthErrorCode tokenExchangeError(RestClientResponseException exception) {
        String error = "";
        try {
            JsonNode body = exception.getResponseBodyAs(JsonNode.class);
            if (body != null) error = body.path("error").asString("");
        } catch (RuntimeException ignored) {
            // JSON이 아닌 응답은 제공자 연결 오류로 처리한다.
        }
        // 응답 원문·인가 코드·시크릿은 기록하지 않고 알려진 오류 식별자만 기록한다.
        String diagnostic = switch (error) {
            case "invalid_grant", "invalid_request", "invalid_client", "unauthorized_client",
                 "redirect_uri_mismatch" -> error;
            default -> "unknown";
        };
        log.warn("구글 토큰 교환 실패: status={}, error={}", exception.getStatusCode().value(), diagnostic);
        return "invalid_grant".equals(error) || "invalid_request".equals(error)
                ? AuthErrorCode.SOCIAL_CODE_INVALID : AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE;
    }

    private JsonNode userInfo(String accessToken) {
        try {
            var response = http.get().uri(userInfoUri)
                    .headers(headers -> headers.setBearerAuth(accessToken)).retrieve().body(JsonNode.class);
            if (response == null) {
                throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
            }
            return response;
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
    }

    private SocialUser profile(Provider provider, String subject, String nickname, String email, String imageUrl) {
        if (subject == null || subject.isBlank() || subject.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        return new SocialUser(provider, subject, nickname, email, secureImageUrl(imageUrl));
    }

    private String secureImageUrl(String value) {
        if (value == null || value.length() > 2048) {
            return null;
        }
        try {
            var uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
                return value;
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return null;
    }

}
