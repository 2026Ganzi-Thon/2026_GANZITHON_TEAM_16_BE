package com.mowoo.mowoo_backend.chat.ai;

import com.mowoo.mowoo_backend.ai.solar.SolarApiException;
import com.mowoo.mowoo_backend.ai.solar.SolarChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SolarDiaryComparisonAiClientTest {
    private final SolarChatClient solar = mock(SolarChatClient.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final SolarDiaryComparisonAiClient client = new SolarDiaryComparisonAiClient(solar, json);

    private static final DiaryComparisonAiClient.Request REQUEST = new DiaryComparisonAiClient.Request(
            "다정한 모우", "따뜻하게 들어 주는 모우", "내일 발표가 걱정돼요.",
            LocalDate.of(2026, 9, 15), "발표를 앞둔 날", "발표 전날 잠이 오지 않았다.");

    @Test
    void 캐릭터는_지시문에_고민과_과거_일기는_입력에_담아_답변을_돌려준다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\" 그때도 잘 지나왔어요. \"}"));

        assertThat(client.compare(REQUEST)).isEqualTo("그때도 잘 지나왔어요.");

        var prompt = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(prompt.capture());
        assertThat(prompt.getValue().instructions()).contains("다정한 모우", "따뜻하게 들어 주는 모우");
        var input = json.readTree(prompt.getValue().input());
        assertThat(input.path("concern").asString()).isEqualTo("내일 발표가 걱정돼요.");
        assertThat(input.path("pastDiary").path("entryDate").asString()).isEqualTo("2026-09-15");
        assertThat(input.path("pastDiary").path("title").asString()).isEqualTo("발표를 앞둔 날");
        assertThat(input.path("pastDiary").path("content").asString()).isEqualTo("발표 전날 잠이 오지 않았다.");
        assertThat(prompt.getValue().input()).doesNotContain("다정한 모우", "diaryId", "sessionId", "userId");
        assertThat(prompt.getValue().schema()).containsEntry("additionalProperties", false);
    }

    @Test
    void 긴_일기는_앞부분만_보낸다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\"답변\"}"));

        client.compare(new DiaryComparisonAiClient.Request("모우", null, "고민",
                LocalDate.of(2026, 9, 15), "제목", "가".repeat(6000)));

        var prompt = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(prompt.capture());
        assertThat(json.readTree(prompt.getValue().input()).path("pastDiary").path("content").asString()).hasSize(5000);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"reply\":\"\"}",
            "{\"reply\":\"  \"}",
            "{\"reply\":1}",
            "{}",
            "{\"reply\":\"답변\",\"extra\":true}",
            "답변"
    })
    void 형식이_다른_응답은_실패로_본다(String content) {
        when(solar.complete(any())).thenReturn(result(content));

        assertThatThrownBy(() -> client.compare(REQUEST)).isInstanceOf(ChatAiException.class);
    }

    @Test
    void Solar_예외는_ChatAiException으로_바꾼다() {
        when(solar.complete(any())).thenThrow(new SolarApiException(SolarApiException.Kind.QUOTA));

        assertThatThrownBy(() -> client.compare(REQUEST))
                .isInstanceOf(ChatAiException.class)
                .hasCauseInstanceOf(SolarApiException.class);
    }

    @Test
    void 고민이나_일기_내용이_없으면_AI를_부르지_않는다() {
        assertThatThrownBy(() -> client.compare(new DiaryComparisonAiClient.Request("모우", null, " ",
                LocalDate.of(2026, 9, 15), "제목", "본문"))).isInstanceOf(ChatAiException.class);
        assertThatThrownBy(() -> client.compare(new DiaryComparisonAiClient.Request("모우", null, "고민",
                LocalDate.of(2026, 9, 15), "제목", ""))).isInstanceOf(ChatAiException.class);
        verifyNoInteractions(solar);
    }

    private static SolarChatClient.Result result(String content) {
        return new SolarChatClient.Result(content, null);
    }
}
