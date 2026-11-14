package com.mowoo.mowoo_backend.global.config;

import com.mowoo.mowoo_backend.global.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        Info apiInfo = new Info()
                .version("v1.0.0")
                .title("MOWOO API")
                .description("모우 백엔드 API. 로그인 후 받은 accessToken을 Authorize에 입력합니다. "
                        + "인증·재발급·서버 상태 확인 외의 업무 API는 Bearer 액세스 토큰이 필요합니다. "
                        + "성공 응답은 isSuccess/code/message, 실패 응답은 isSuccess/message/error를 포함합니다. "
                        + "시각은 UTC ISO 8601, 기록 날짜는 YYYY-MM-DD이며 캘린더는 Asia/Seoul 기준입니다.");

        String jwtSchemeName = "BearerToken";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .info(apiInfo)
                .addSecurityItem(securityRequirement)
                .components(components);
    }

    @Bean
    public GroupedOpenApi allGroup() {
        return GroupedOpenApi.builder()
                .group("All APIs")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public GlobalOpenApiCustomizer authenticationDocumentation() {
        Set<String> publicAuth = Set.of("/api/v1/auth/kakao", "/api/v1/auth/google", "/api/v1/auth/naver", "/api/v1/auth/reissue");
        return api -> {
            ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(api.getComponents()::addSchemas);
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (path.equals("/health") || (method.name().equals("POST") && publicAuth.contains(path))) {
                    operation.setSecurity(List.of());
                } else if (path.startsWith("/api/v1/")) {
                    operation.getResponses().addApiResponse("401", errorResponse("액세스 토큰 누락·위조·만료 또는 잘못된 토큰 유형"));
                }
                if (path.equals("/api/v1/auth/reissue")) {
                    operation.getResponses().addApiResponse("401", errorResponse("리프레시 토큰 쿠키 누락·만료·폐기"));
                } else if (publicAuth.contains(path)) {
                    operation.getResponses().addApiResponse("400", errorResponse("필수값 누락 또는 유효하지 않은 인가 코드"));
                    operation.getResponses().addApiResponse("409", errorResponse("소셜 계정 연결 충돌"));
                    operation.getResponses().addApiResponse("502", errorResponse("소셜 제공자 연결 또는 클라이언트 설정 오류"));
                }
            }));
        };
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(
                "application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
    }
}
