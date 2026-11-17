package com.mowoo.mowoo_backend.chat.ai;

import com.mowoo.mowoo_backend.ai.solar.SolarApiException;
import com.mowoo.mowoo_backend.ai.solar.SolarChatClient;
import com.mowoo.mowoo_backend.chat.ai.DiarySuggestionAiClient.Suggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SolarDiarySuggestionAiClientTest {
    private final SolarChatClient solar = mock(SolarChatClient.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final SolarDiarySuggestionAiClient client = new SolarDiarySuggestionAiClient(solar, json);

    private static final List<DiarySuggestionAiClient.Candidate> CANDIDATES = List.of(
            new DiarySuggestionAiClient.Candidate(LocalDate.of(2026, 10, 1), "여행", "바다를 보고 왔다."),
            new DiarySuggestionAiClient.Candidate(LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "가".repeat(600)),
            new DiarySuggestionAiClient.Candidate(LocalDate.of(2026, 9, 1), "면접", "면접을 봤다."));

    @Test
    void 요청용_키로_후보를_보내고_고른_순서대로_위치와_권유_문장을_돌려준다() {
        when(solar.complete(any())).thenReturn(result("""
                {"suggestions":[
                  {"key":"d2","invitation":" 예전에도 발표 전에 비슷한 걱정을 적었어. 같이 살펴볼까? "},
                  {"key":"d3","invitation":"면접 때도 떨렸던 기록이 있어. 볼래?"}]}
                """));

        assertThat(client.select(request("발표가 걱정돼요.", CANDIDATES), 3)).containsExactly(
                new Suggestion(1, "예전에도 발표 전에 비슷한 걱정을 적었어. 같이 살펴볼까?"),
                new Suggestion(2, "면접 때도 떨렸던 기록이 있어. 볼래?"));

        var prompt = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(prompt.capture());
        var input = json.readTree(prompt.getValue().input());
        assertThat(input.path("concern").asString()).isEqualTo("발표가 걱정돼요.");
        assertThat(input.path("diaries")).hasSize(3);
        assertThat(input.path("diaries").get(1).path("key").asString()).isEqualTo("d2");
        assertThat(input.path("diaries").get(1).path("entryDate").asString()).isEqualTo("2026-09-15");
        // 긴 본문은 앞부분만 보낸다.
        assertThat(input.path("diaries").get(1).path("content").asString()).hasSize(500);
        assertThat(json.writeValueAsString(prompt.getValue().schema()))
                .contains("\"d1\"", "\"d3\"", "\"invitation\"").doesNotContain("\"d4\"");
        assertThat(prompt.getValue().instructions()).contains("최대 3개", "다정한 모우", "따뜻하게 들어 주는 모우");
    }

    @Test
    void 중복은_처음_것만_넣고_limit까지만_돌려준다() {
        when(solar.complete(any())).thenReturn(result("""
                {"suggestions":[{"key":"d3","invitation":"첫 권유"},{"key":"d3","invitation":"두 번째 권유"},
                  {"key":"d1","invitation":"바다 권유"},{"key":"d2","invitation":"발표 권유"}]}
                """));

        assertThat(client.select(request("고민", CANDIDATES), 2))
                .containsExactly(new Suggestion(2, "첫 권유"), new Suggestion(0, "바다 권유"));
    }

    @Test
    void 관련_일기가_없으면_빈_목록이다() {
        when(solar.complete(any())).thenReturn(result("{\"suggestions\":[]}"));

        assertThat(client.select(request("고민", CANDIDATES), 3)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"suggestions\":[{\"key\":\"d9\",\"invitation\":\"권유\"}]}",
            "{\"suggestions\":[{\"key\":1,\"invitation\":\"권유\"}]}",
            "{\"suggestions\":[{\"key\":\"d1\"}]}",
            "{\"suggestions\":[{\"key\":\"d1\",\"invitation\":\" \"}]}",
            "{\"suggestions\":[{\"key\":\"d1\",\"invitation\":1}]}",
            "{\"suggestions\":[{\"key\":\"d1\",\"invitation\":\"권유\",\"extra\":true}]}",
            "{\"suggestions\":[\"d1\"]}",
            "{\"suggestions\":{\"key\":\"d1\",\"invitation\":\"권유\"}}",
            "{\"diaryKeys\":[\"d1\"]}",
            "{}",
            "{\"suggestions\":[],\"extra\":true}",
            "d1"
    })
    void 형식이_다르거나_없는_키나_빈_권유_문장은_실패로_본다(String content) {
        when(solar.complete(any())).thenReturn(result(content));

        assertThatThrownBy(() -> client.select(request("고민", CANDIDATES), 3)).isInstanceOf(ChatAiException.class);
    }

    @Test
    void 지나치게_긴_권유_문장은_실패로_본다() {
        when(solar.complete(any())).thenReturn(result(
                "{\"suggestions\":[{\"key\":\"d1\",\"invitation\":\"" + "가".repeat(301) + "\"}]}"));

        assertThatThrownBy(() -> client.select(request("고민", CANDIDATES), 3)).isInstanceOf(ChatAiException.class);
    }

    @Test
    void Solar_예외는_ChatAiException으로_바꾼다() {
        when(solar.complete(any())).thenThrow(new SolarApiException(SolarApiException.Kind.CONNECTION));

        assertThatThrownBy(() -> client.select(request("고민", CANDIDATES), 3))
                .isInstanceOf(ChatAiException.class)
                .hasCauseInstanceOf(SolarApiException.class);
    }

    @Test
    void 고민이나_후보나_캐릭터가_없으면_AI를_부르지_않는다() {
        assertThatThrownBy(() -> client.select(request(" ", CANDIDATES), 3)).isInstanceOf(ChatAiException.class);
        assertThatThrownBy(() -> client.select(request("고민", List.of()), 3)).isInstanceOf(ChatAiException.class);
        assertThatThrownBy(() -> client.select(
                new DiarySuggestionAiClient.Request(null, null, "고민", CANDIDATES), 3)).isInstanceOf(ChatAiException.class);
        assertThatThrownBy(() -> client.select(null, 3)).isInstanceOf(ChatAiException.class);
        verifyNoInteractions(solar);
    }

    private static DiarySuggestionAiClient.Request request(
            String concern, List<DiarySuggestionAiClient.Candidate> candidates) {
        return new DiarySuggestionAiClient.Request("다정한 모우", "따뜻하게 들어 주는 모우", concern, candidates);
    }

    private static SolarChatClient.Result result(String content) {
        return new SolarChatClient.Result(content, null);
    }
}
