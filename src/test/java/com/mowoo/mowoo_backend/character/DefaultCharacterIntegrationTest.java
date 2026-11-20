package com.mowoo.mowoo_backend.character;

import com.mowoo.mowoo_backend.character.entity.AiCharacter;
import com.mowoo.mowoo_backend.character.entity.UserCharacter;
import com.mowoo.mowoo_backend.character.repository.AiCharacterRepository;
import com.mowoo.mowoo_backend.character.repository.UserCharacterRepository;
import com.mowoo.mowoo_backend.character.service.DefaultCharacterService;
import com.mowoo.mowoo_backend.chat.repository.ChatMessageRepository;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatSummaryAiClient;
import com.mowoo.mowoo_backend.chat.service.ChatService;
import com.mowoo.mowoo_backend.login.client.KakaoClient;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DefaultCharacterIntegrationTest {

    private static final List<String> DEFAULT_CODES = List.of("KIND", "COOL", "CHEERFUL");
    private static final String CHAT = "/api/v1/chat-sessions";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private AiCharacterRepository characterRepository;

    @Autowired
    private UserCharacterRepository ownershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultCharacterService defaultCharacterService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository messageRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    @MockitoBean
    private ChatAiClient chatAiClient;

    @MockitoBean
    private ChatSummaryAiClient chatSummaryAiClient;

    @Test
    void loginGrantsDefaultsAndAllowsChatRightAway() throws Exception {
        when(chatAiClient.reply(any())).thenReturn("테스트 답변");
        when(chatSummaryAiClient.summarize(any()))
                .thenReturn(new ChatSummaryAiClient.Summary("테스트 대화", "테스트 요약"));
        var defaults = characterRepository.findByCodeInAndPremiumFalse(DEFAULT_CODES);
        assertThat(defaults).extracting(AiCharacter::getName)
                .containsExactlyInAnyOrder("다정한 모우", "냉철한 모우", "유쾌한 모우");
        defaultCharacterService.run(new DefaultApplicationArguments());
        var defaultIds = defaults.stream().map(AiCharacter::getId).toList();
        assertThat(characterRepository.findByCodeInAndPremiumFalse(DEFAULT_CODES))
                .extracting(AiCharacter::getId).containsExactlyInAnyOrderElementsOf(defaultIds);

        AiCharacter premium = characterRepository.save(new AiCharacter(
                "TEST_PREMIUM", "테스트 유료 캐릭터", null, null, true));
        Login login = login("123456789");
        assertThat(ownershipRepository.findCharacterIdsByUserId(login.userId()))
                .containsExactlyInAnyOrderElementsOf(defaultIds);
        mvc.perform(post(CHAT).header(HttpHeaders.AUTHORIZATION, login.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(characterRequest(premium.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHARACTER_NOT_OWNED"));

        UUID defaultId = defaultIds.getFirst();
        ownershipRepository.deleteById(new UserCharacter.Id(login.userId(), defaultId));
        Login returning = login("123456789");
        assertThat(returning.userId()).isEqualTo(login.userId());
        assertThat(ownershipRepository.findCharacterIdsByUserId(returning.userId()))
                .containsExactlyInAnyOrderElementsOf(defaultIds);

        UUID sessionId = startChat(returning, defaultId);
        for (int turn = 1; turn <= 5; turn++) {
            mvc.perform(post(CHAT + "/{sessionId}/messages", sessionId)
                            .header(HttpHeaders.AUTHORIZATION, returning.authorization())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"테스트 메시지\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.completedTurns").value(turn))
                    .andExpect(jsonPath("$.canGenerateDiary").value(turn == 5));
        }
        mvc.perform(get(CHAT + "/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, returning.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.items.length()").value(10));
        mvc.perform(patch(CHAT + "/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, returning.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"END\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_END_SUCCESS"));

        Login otherUser = login("987654321");
        UUID otherSessionId = startChat(otherUser, defaultId);
        chatService.deleteAllSessions(returning.userId());
        assertThat(messageRepository.findBySessionIdOrderBySequenceNo(sessionId)).isEmpty();
        assertThat(userRepository.existsById(returning.userId())).isTrue();
        mvc.perform(get(CHAT + "/{sessionId}", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, returning.authorization()))
                .andExpect(status().isNotFound());
        mvc.perform(get(CHAT + "/{sessionId}", otherSessionId)
                        .header(HttpHeaders.AUTHORIZATION, otherUser.authorization()))
                .andExpect(status().isOk());
    }

    private Login login(String subject) throws Exception {
        when(kakaoClient.getUser(subject))
                .thenReturn(new KakaoClient.KakaoUser(subject, "테스트 사용자", null, null));
        MvcResult result = mvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + subject + "\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = body(result);
        return new Login(UUID.fromString(response.path("id").asString()),
                "Bearer " + response.path("accessToken").asString());
    }

    private UUID startChat(Login login, UUID characterId) throws Exception {
        MvcResult result = mvc.perform(post(CHAT).header(HttpHeaders.AUTHORIZATION, login.authorization())
                        .contentType(MediaType.APPLICATION_JSON).content(characterRequest(characterId)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(body(result).path("id").asString());
    }

    private String characterRequest(UUID characterId) {
        return "{\"characterId\":\"" + characterId + "\"}";
    }

    private JsonNode body(MvcResult result) {
        return jsonMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record Login(UUID userId, String authorization) {
    }
}
