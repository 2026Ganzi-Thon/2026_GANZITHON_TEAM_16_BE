package com.mowoo.mowoo_backend.login.client;

import com.mowoo.mowoo_backend.global.exception.BusinessException;
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
import java.time.Duration;

@Component
public class KakaoClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUri;
    private final String userInfoUri;

    public KakaoClient(
            @Value("${kakao.client-id}") String clientId,
            @Value("${kakao.client-secret}") String clientSecret,
            @Value("${kakao.redirect-uri}") String redirectUri,
            @Value("${kakao.token-uri}") String tokenUri,
            @Value("${kakao.user-info-uri}") String userInfoUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public KakaoUser getUser(String code) {
        String accessToken = exchangeCode(code);

        JsonNode response;
        try {
            response = restClient.get()
                    .uri(userInfoUri, uri -> uri
                            .queryParam("secure_resource", true)
                            .queryParam("property_keys", "{properties}")
                            .build("[\"kakao_account.profile\",\"kakao_account.email\"]"))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }

        JsonNode id = response == null ? null : response.path("id");
        if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }

        JsonNode account = response.path("kakao_account");
        JsonNode profile = account.path("profile");
        String nickname = profile.path("nickname").asString("");
        String imageUrl = profileImageUrl(profile.path("profile_image_url").asString(null));
        String email = account.path("is_email_valid").asBoolean(false) && account.path("is_email_verified").asBoolean(false)
                ? account.path("email").asString(null) : null;
        return new KakaoUser(Long.toString(id.longValue()), nickname, email, imageUrl);
    }

    private String profileImageUrl(String value) {
        if (value == null || value.length() > 2048) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null) {
                return value;
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return null;
    }

    private String exchangeCode(String code) {
        if (clientId.isBlank() || redirectUri.isBlank()) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", clientId);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400) {
                throw new BusinessException(AuthErrorCode.SOCIAL_CODE_INVALID);
            }
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }

        String accessToken = response == null ? "" : response.path("access_token").asString("");
        if (accessToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        return accessToken;
    }

    public record KakaoUser(String subject, String nickname, String email, String profileImageUrl) {}
}
