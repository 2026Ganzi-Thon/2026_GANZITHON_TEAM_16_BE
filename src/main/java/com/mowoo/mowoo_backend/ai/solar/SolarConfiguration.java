package com.mowoo.mowoo_backend.ai.solar;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.net.URI;
import java.util.Set;
import java.time.Duration;

@Configuration
public class SolarConfiguration {
    @Bean("solarRestClient")
    public RestClient solarRestClient(SolarProperties properties) {
        if (properties.getConnectTimeoutMs() <= 0 || properties.getReadTimeoutMs() <= 0
                || properties.getMaxOutputTokens() <= 0 || properties.getMaxInputChars() <= 0) {
            throw new IllegalArgumentException("Upstage 시간 제한과 입력·출력 한도는 양수여야 합니다.");
        }
        URI base;
        try {
            base = URI.create(properties.getBaseUrl());
            if (!"https".equals(base.getScheme())
                    || !Set.of("api.upstage.ai", "kr.api.upstage.ai").contains(base.getHost())
                    || (base.getPort() != -1 && base.getPort() != 443)
                    || base.getUserInfo() != null || base.getRawQuery() != null || base.getFragment() != null
                    || !base.getPath().replaceAll("/+$", "").equals("/v1")) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Upstage 기본 주소는 공식 HTTPS API의 /v1 경로여야 합니다.");
        }
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder().baseUrl(base.toString().replaceAll("/+$", "")).requestFactory(factory).build();
    }
}
