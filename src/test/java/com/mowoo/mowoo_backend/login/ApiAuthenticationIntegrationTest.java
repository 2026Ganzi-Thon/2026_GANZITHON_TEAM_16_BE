package com.mowoo.mowoo_backend.login;

import com.mowoo.mowoo_backend.login.client.GoogleClient;
import com.mowoo.mowoo_backend.login.client.KakaoClient;
import com.mowoo.mowoo_backend.login.client.NaverClient;
import com.mowoo.mowoo_backend.login.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ApiAuthenticationIntegrationTest {
    private static final Set<String> PUBLIC_AUTH = Set.of(
            "/api/v1/auth/kakao", "/api/v1/auth/google", "/api/v1/auth/naver", "/api/v1/auth/reissue");
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    @DynamicPropertySource
    static void requireTestDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().matches("/(?:test|test[_-].*|.*[_-]test|.*[_-]test[_-].*)")) {
            throw new IllegalStateException("인증 통합 테스트는 전용 MySQL 테스트 DB에서 실행하세요.");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JwtProvider tokens;
    @Autowired JwtEncoder encoder;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    @MockitoBean KakaoClient kakao;
    @MockitoBean GoogleClient google;
    @MockitoBean NaverClient naver;

    @Test
    void everyProtectedOperationRejectsAnonymousRequests() throws Exception {
        assertProtectedOperationsReject(null);
    }

    @Test
    void everyProtectedOperationRejectsMalformedTokens() throws Exception {
        assertProtectedOperationsReject("not-a-jwt");
    }

    @Test
    void everyProtectedOperationRejectsTamperedSignatures() throws Exception {
        String token = tokens.createAccessToken(UUID.randomUUID(), "test-session");
        int signature = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signature) == 'a' ? 'b' : 'a';
        assertProtectedOperationsReject(token.substring(0, signature) + replacement + token.substring(signature + 1));
    }

    @Test
    void everyProtectedOperationRejectsExpiredTokens() throws Exception {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(UUID.randomUUID().toString())
                .issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(300))
                .claim("type", "ACCESS").claim("session_hash", "test-session").build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertProtectedOperationsReject(token);
    }

    @Test
    void refreshTokensCannotBeUsedAsAccessTokensOnAnyProtectedOperation() throws Exception {
        assertProtectedOperationsReject(tokens.createRefreshToken(UUID.randomUUID()).getTokenValue());
    }

    @Test
    void loginHealthAndDocumentationAreReachableBeforeLogin() throws Exception {
        for (String provider : List.of("kakao", "google", "naver")) {
            mvc.perform(post("/api/v1/auth/" + provider).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void realLoginTokenAllowsBusinessReadsAndRefreshCookieWorksWithoutAccessToken() throws Exception {
        when(kakao.getUser("security-test")).thenReturn(new KakaoClient.KakaoUser("security-user", "인증 테스트", null, null));
        var login = mvc.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"security-test\"}"))
                .andExpect(status().isOk()).andReturn();
        String accessToken = json.readTree(login.getResponse().getContentAsByteArray()).path("accessToken").asString();
        String cookieValue = login.getResponse().getHeader(HttpHeaders.SET_COOKIE).split(";", 2)[0]
                .substring("refresh_token=".length());
        Cookie refresh = new Cookie("refresh_token", cookieValue);
        for (String path : List.of("/api/v1/me", "/api/v1/characters", "/api/v1/chat-sessions",
                "/api/v1/diaries", "/api/v1/emotion-types", "/api/v1/calendar?month=2026-11", "/api/v1/calendar/recap?month=2026-11")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/auth/reissue").cookie(refresh)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/reissue").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void everyDocumentedOperationHasDescriptionsAndCorrectAuthenticationRequirements() throws Exception {
        assertDocumentation("/v3/api-docs");
        assertDocumentation("/v3/api-docs/All APIs");
    }

    private void assertDocumentation(String docsPath) throws Exception {
        var result = mvc.perform(get(docsPath)).andExpect(status().isOk()).andReturn();
        Map<?, ?> document = json.readValue(result.getResponse().getContentAsByteArray(), Map.class);
        Map<?, ?> paths = (Map<?, ?>) document.get("paths");
        int operations = 0;
        for (var path : paths.entrySet()) {
            Map<?, ?> item = (Map<?, ?>) path.getValue();
            for (var method : item.entrySet()) {
                if (!HTTP_METHODS.contains(method.getKey())) continue;
                operations++;
                Map<?, ?> operation = (Map<?, ?>) method.getValue();
                assertThat((String) operation.get("summary")).as("%s %s summary", method.getKey(), path.getKey()).isNotBlank();
                assertThat((String) operation.get("description")).as("%s %s description", method.getKey(), path.getKey()).isNotBlank();
                List<?> security = (List<?>) (operation.containsKey("security") ? operation.get("security") : document.get("security"));
                boolean publicOperation = path.getKey().equals("/health")
                        || (method.getKey().equals("post") && PUBLIC_AUTH.contains(path.getKey()));
                if (publicOperation) assertThat(security).as("%s should be public", path.getKey()).isEmpty();
                else {
                    assertThat(security).as("%s requires JWT", path.getKey()).isNotEmpty();
                    assertThat(((Map<?, ?>) operation.get("responses")).containsKey("401")).isTrue();
                }
                if (operation.get("parameters") instanceof List<?> parameters) {
                    for (Object value : parameters) assertThat(((Map<?, ?>) value).get("name")).isNotEqualTo("jwt");
                }
            }
        }
        assertThat(operations).isEqualTo(protectedEndpoints().size() + PUBLIC_AUTH.size() + 1);
        Map<?, ?> schemas = (Map<?, ?>) ((Map<?, ?>) document.get("components")).get("schemas");
        Map<?, ?> googleRequest = (Map<?, ?>) schemas.get("SocialCodeRequest");
        assertThat(((Map<?, ?>) googleRequest.get("properties")).keySet()).isEqualTo(Set.of("code"));
        assertThat(schemas.containsKey("ErrorResponse")).isTrue();
    }

    private void assertProtectedOperationsReject(String token) throws Exception {
        List<Endpoint> endpoints = protectedEndpoints();
        assertThat(endpoints.size()).isGreaterThan(20);
        for (Endpoint endpoint : endpoints) {
            var request = request(endpoint.method(), endpoint.path()).contentType(MediaType.APPLICATION_JSON).content("{}");
            if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            mvc.perform(request).andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("%s %s", endpoint.method(), endpoint.path()).isEqualTo(401))
                    .andExpect(jsonPath("$.isSuccess").value(false)).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }

    private List<Endpoint> protectedEndpoints() {
        var endpoints = new ArrayList<Endpoint>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.mowoo.mowoo_backend")) return;
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/v1/")) continue;
                mapping.getMethodsCondition().getMethods().forEach(method -> {
                    if (method.name().equals("POST") && PUBLIC_AUTH.contains(path)) return;
                    endpoints.add(new Endpoint(HttpMethod.valueOf(method.name()), path.replaceAll("\\{[^}]+}", "00000000-0000-0000-0000-000000000001")));
                });
            }
        });
        return endpoints;
    }

    private record Endpoint(HttpMethod method, String path) {}
}
