package com.mowoo.mowoo_backend.chat.ai;

import com.mowoo.mowoo_backend.ai.solar.SolarApiException;
import com.mowoo.mowoo_backend.ai.solar.SolarChatClient;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SolarChatSummaryAiClientTest {
    private final SolarChatClient solar = mock(SolarChatClient.class);
    private final SolarChatSummaryAiClient client = new SolarChatSummaryAiClient(solar, JsonMapper.builder().build());

    private static final List<ChatAiRequest.Message> MESSAGES = List.of(
            new ChatAiRequest.Message(SenderRole.USER, "오늘 면접이 있었어요."),
            new ChatAiRequest.Message(SenderRole.ASSISTANT, "어땠어요?"),
            new ChatAiRequest.Message(SenderRole.USER, "너무 떨렸어요."));

    @Test
    void 대화만_입력에_담아_제목과_요약을_돌려준다() {
        when(solar.complete(any())).thenReturn(result(
                "{\"title\":\" 면접을 앞둔 날 \",\"summary\":\" 면접 걱정을 이야기했어요. \"}"));

        var summary = client.summarize(MESSAGES);

        assertThat(summary).isEqualTo(new ChatSummaryAiClient.Summary("면접을 앞둔 날", "면접 걱정을 이야기했어요."));
        var prompt = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(prompt.capture());
        assertThat(prompt.getValue().input())
                .contains("오늘 면접이 있었어요.", "너무 떨렸어요.", "senderRole", "ASSISTANT")
                .doesNotContain("sessionId", "userId", "createdAt");
        assertThat(prompt.getValue().schema()).containsEntry("additionalProperties", false);
        assertThat(prompt.getValue().schema().get("required"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder("title", "summary");
    }

    static List<String> invalidContents() {
        return List.of(
                "{\"title\":\"\",\"summary\":\"요약\"}",
                "{\"title\":\"제목\",\"summary\":\"  \"}",
                "{\"title\":\"" + "가".repeat(151) + "\",\"summary\":\"요약\"}",
                "{\"title\":\"제목\",\"summary\":\"" + "가".repeat(1001) + "\"}",
                "{\"title\":\"제목\",\"summary\":7}",
                "{\"title\":\"제목\"}",
                "{\"title\":\"제목\",\"summary\":\"요약\",\"extra\":true}",
                "요약");
    }

    @ParameterizedTest
    @MethodSource("invalidContents")
    void 형식이_다르거나_길이를_벗어난_응답은_실패로_본다(String content) {
        when(solar.complete(any())).thenReturn(result(content));

        assertThatThrownBy(() -> client.summarize(MESSAGES)).isInstanceOf(ChatAiException.class);
    }

    @Test
    void 제목은_150자까지_허용한다() {
        String title = "가".repeat(150);
        when(solar.complete(any())).thenReturn(result("{\"title\":\"" + title + "\",\"summary\":\"요약\"}"));

        assertThat(client.summarize(MESSAGES).title()).isEqualTo(title);
    }

    @Test
    void Solar_예외는_ChatAiException으로_바꾼다() {
        when(solar.complete(any())).thenThrow(new SolarApiException(SolarApiException.Kind.SERVER));

        assertThatThrownBy(() -> client.summarize(MESSAGES))
                .isInstanceOf(ChatAiException.class)
                .hasCauseInstanceOf(SolarApiException.class);
    }

    @Test
    void 메시지가_없으면_AI를_부르지_않는다() {
        assertThatThrownBy(() -> client.summarize(List.of())).isInstanceOf(ChatAiException.class);
        verifyNoInteractions(solar);
    }

    private static SolarChatClient.Result result(String content) {
        return new SolarChatClient.Result(content, null);
    }
}
