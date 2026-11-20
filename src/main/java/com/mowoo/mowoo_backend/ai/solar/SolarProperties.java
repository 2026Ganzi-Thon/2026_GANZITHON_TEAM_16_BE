package com.mowoo.mowoo_backend.ai.solar;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// toString에 API 키가 포함되지 않도록 Data/record 대신 접근자만 생성한다.
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "upstage")
public class SolarProperties {
    private boolean enabled;
    private String apiKey = "";
    private String model = "solar-pro4";
    private String baseUrl = "https://api.upstage.ai/v1";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 30000;
    private int maxOutputTokens = 2048;
    private int maxInputChars = 100000;
}
