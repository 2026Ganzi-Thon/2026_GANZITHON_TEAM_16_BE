package com.mowoo.mowoo_backend.ai.solar;

import com.mowoo.mowoo_backend.ai.AiUnavailableException;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.chat.service.ChatTranscript;
import com.mowoo.mowoo_backend.diary.ai.SolarDiaryAiClient;
import com.mowoo.mowoo_backend.emotion.ai.EmotionAiClient;
import com.mowoo.mowoo_backend.emotion.ai.SolarEmotionAiClient;
import com.mowoo.mowoo_backend.calendar.ai.SolarMonthlyRecapAiClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SolarDomainAdaptersTest {
    private final SolarChatClient solar = mock(SolarChatClient.class);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void diaryPromptIncludesOnlyNecessaryConversationData() {
        UUID sessionId = UUID.randomUUID();
        var transcript = new ChatTranscript(sessionId, Instant.now(), null, 1, false, List.of(
                new ChatTranscript.Message(1, SenderRole.USER, null, "오늘 도서관에 갔다.", Instant.now())));
        when(solar.complete(any())).thenReturn(new SolarChatClient.Result("{\"title\":\"도서관\",\"content\":\"오늘 도서관에 갔다.\"}", null));
        var result = new SolarDiaryAiClient(solar, json).generate(transcript);
        assertThat(result.title()).isEqualTo("도서관");
        var request = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(request.capture());
        assertThat(request.getValue().input()).contains("오늘 도서관에 갔다.")
                .doesNotContain(sessionId.toString(), "sessionId", "startedAt", "Authorization");
        assertThat(request.getValue().schema()).containsEntry("additionalProperties", false);
    }

    @Test
    void emotionSchemaUsesDatabaseCodesAndWrongTypesAreRejected() {
        var adapter = new SolarEmotionAiClient(solar, json);
        when(solar.complete(any())).thenReturn(new SolarChatClient.Result(
                "{\"emotionCodes\":[\"CALM\"],\"primaryEmotionCode\":\"CALM\"}", null));
        var result = adapter.suggest(new EmotionAiClient.DiaryText("제목", "본문"),
                List.of(new EmotionAiClient.Option("CALM", "평온한 날")));
        assertThat(result.emotionCodes()).containsExactly("CALM");
        var request = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(request.capture());
        assertThat(json.writeValueAsString(request.getValue().schema())).contains("CALM").doesNotContain("HAPPY");
        when(solar.complete(any())).thenReturn(new SolarChatClient.Result(
                "{\"emotionCodes\":[123],\"primaryEmotionCode\":\"CALM\"}", null));
        assertThatThrownBy(() -> adapter.suggest(new EmotionAiClient.DiaryText("제목", "본문"),
                List.of(new EmotionAiClient.Option("CALM", "평온한 날"))))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void extraFieldsAndMissingFieldsCannotEnterInternalDtos() {
        when(solar.complete(any())).thenReturn(new SolarChatClient.Result("{\"concernSummary\":\"요약\",\"extra\":true}", null));
        assertThatThrownBy(() -> new SolarMonthlyRecapAiClient(solar, json).summarize(List.of("본문")))
                .isInstanceOf(AiUnavailableException.class);
        when(solar.complete(any())).thenReturn(new SolarChatClient.Result("{\"title\":\"제목\"}", null));
        var transcript = new ChatTranscript(UUID.randomUUID(), Instant.now(), null, 1, false, List.of(
                new ChatTranscript.Message(1, SenderRole.USER, null, "본문", Instant.now())));
        assertThatThrownBy(() -> new SolarDiaryAiClient(solar, json).generate(transcript))
                .isInstanceOf(AiUnavailableException.class);
    }
}
