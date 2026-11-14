package com.mowoo.mowoo_backend.login.client;

import com.mowoo.mowoo_backend.global.exception.BusinessException;
import com.mowoo.mowoo_backend.login.domain.UserAuthIdentity.Provider;
import com.mowoo.mowoo_backend.login.exception.AuthErrorCode;
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

@Component
public class NaverClient {
    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUri;
    private final String userInfoUri;

    public NaverClient(
            @Value("${naver.client-id:}") String clientId,
            @Value("${naver.client-secret:}") String clientSecret,
            @Value("${naver.redirect-uri:}") String redirectUri,
            @Value("${naver.token-uri}") String tokenUri,
            @Value("${naver.user-info-uri}") String userInfoUri
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
    }

    public SocialUser getUser(String code, String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(AuthErrorCode.SOCIAL_CODE_INVALID);
        }
        var response = userInfo(exchange(code, state));
        if (!"00".equals(response.path("resultcode").asString())) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        var account = response.path("response");
        String name = account.path("name").asString("");
        if (name.isBlank()) {
            name = account.path("nickname").asString("");
        }
        return profile(Provider.NAVER, account.path("id").asString(null),
                name, account.path("email").asString(null),
                account.path("profile_image").asString(null));
    }

    private String exchange(String code, String state) {
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
        if (state != null) {
            form.add("state", state);
        }
        JsonNode response;
        try {
            response = http.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400) {
                throw new BusinessException(AuthErrorCode.SOCIAL_CODE_INVALID);
            }
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
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
