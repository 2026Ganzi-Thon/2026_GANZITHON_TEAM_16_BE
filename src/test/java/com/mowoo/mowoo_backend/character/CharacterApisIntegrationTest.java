package com.mowoo.mowoo_backend.character;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.character.repository.AiCharacterRepository;
import com.mowoo.mowoo_backend.character.repository.UserCharacterRepository;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatSummaryAiClient;
import com.mowoo.mowoo_backend.login.client.KakaoClient;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class CharacterApisIntegrationTest {
    @DynamicPropertySource
    static void requireTestDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().matches("/(?:test|test[_-].*|.*[_-]test|.*[_-]test[_-].*)")) {
            throw new IllegalStateException("캐릭터 API 테스트는 전용 MySQL 테스트 DB에서 실행하세요.");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired AiCharacterRepository characters;
    @Autowired UserCharacterRepository ownerships;
    @Autowired UserRepository users;
    @MockitoBean KakaoClient kakao;
    @MockitoBean ChatAiClient chatAi;
    @MockitoBean ChatSummaryAiClient summaryAi;
    private Login owner;
    private AiCharacter unowned;

    @BeforeEach
    void setUp() throws Exception {
        owner = login("character-owner");
        unowned = characters.saveAndFlush(new AiCharacter("TEST_PREMIUM", "테스트 캐릭터", null, null, true));
    }

    @Test
    void listsAllCharactersWithOwnershipInCodeOrder() throws Exception {
        var result = mvc.perform(get("/api/v1/characters").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CHARACTER_LIST_READ_SUCCESS"))
                .andExpect(jsonPath("$.message").value("캐릭터 목록 조회가 완료됐어요."))
                .andExpect(jsonPath("$.items.length()").value(4)).andReturn();
        var codes = new ArrayList<String>();
        for (var item : json.readTree(result.getResponse().getContentAsByteArray()).path("items")) {
            codes.add(item.path("code").asString());
            assertThat(item.path("isOwned").asBoolean()).isEqualTo(!item.path("isPremium").asBoolean());
        }
        assertThat(codes).containsExactly("CHEERFUL", "COOL", "KIND", "TEST_PREMIUM");
    }

    @Test
    void filtersOwnershipForTheCurrentUserOnly() throws Exception {
        Login other = login("character-other");
        ownerships.saveAndFlush(new UserCharacter(users.getReferenceById(other.id()), unowned));
        mvc.perform(get("/api/v1/characters").param("owned", "true").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3));
        mvc.perform(get("/api/v1/characters").param("owned", "false").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("TEST_PREMIUM"))
                .andExpect(jsonPath("$.items[0].isOwned").value(false));
        mvc.perform(get("/api/v1/characters").param("owned", "false").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void returnsOwnedCharacterDetailsAndSeparateCodes() throws Exception {
        var character = characters.findByCodeInAndPremiumFalse(java.util.List.of("KIND")).getFirst();
        mvc.perform(get("/api/v1/characters/{id}", character.getId()).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CHARACTER_READ_SUCCESS"))
                .andExpect(jsonPath("$.message").value("캐릭터 조회가 완료됐어요."))
                .andExpect(jsonPath("$.id").value(character.getId().toString()))
                .andExpect(jsonPath("$.characterCode").value("KIND"))
                .andExpect(jsonPath("$.name").value("다정한 모우"))
                .andExpect(jsonPath("$.isPremium").value(false)).andExpect(jsonPath("$.isOwned").value(true));
    }

    @Test
    void allowsViewingAnUnownedCharacterWithNullableMetadata() throws Exception {
        mvc.perform(get("/api/v1/characters/{id}", unowned.getId()).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.characterCode").value("TEST_PREMIUM"))
                .andExpect(jsonPath("$.description").isEmpty()).andExpect(jsonPath("$.imageUrl").isEmpty())
                .andExpect(jsonPath("$.isPremium").value(true)).andExpect(jsonPath("$.isOwned").value(false));
    }

    @Test
    void rejectsUnknownCharactersAndInvalidParameters() throws Exception {
        mvc.perform(get("/api/v1/characters/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("CHARACTER_NOT_FOUND"));
        mvc.perform(get("/api/v1/characters/not-a-uuid").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/characters").param("owned", "invalid").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsRequestsWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/characters")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/characters/{id}", unowned.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokensWhoseUserNoLongerExists() throws Exception {
        UUID missingUser = UUID.randomUUID();
        mvc.perform(get("/api/v1/characters").with(jwt().jwt(token -> token.subject(missingUser.toString()))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/characters/{id}", unowned.getId())
                        .with(jwt().jwt(token -> token.subject(missingUser.toString()))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private Login login(String subject) throws Exception {
        when(kakao.getUser(subject)).thenReturn(new KakaoClient.KakaoUser(subject, "테스트 사용자", null, null));
        var result = mvc.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + subject + "\"}"))
                .andExpect(status().isOk()).andReturn();
        var response = json.readTree(result.getResponse().getContentAsByteArray());
        return new Login(UUID.fromString(response.path("id").asString()),
                "Bearer " + response.path("accessToken").asString());
    }

    private record Login(UUID id, String bearer) {}
}
