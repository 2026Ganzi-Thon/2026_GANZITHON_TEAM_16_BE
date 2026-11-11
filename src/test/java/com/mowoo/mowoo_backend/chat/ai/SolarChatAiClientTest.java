package com.mowoo.mowoo_backend.chat.ai;

import com.mowoo.mowoo_backend.ai.solar.SolarApiException;
import com.mowoo.mowoo_backend.ai.solar.SolarChatClient;
import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

class SolarChatAiClientTest {
    private final SolarChatClient solar = mock(SolarChatClient.class);
    private final SolarChatAiClient client = new SolarChatAiClient(solar, JsonMapper.builder().build());

    private static final ChatAiRequest REQUEST = new ChatAiRequest("다정한 모우", "따뜻하게 들어 주는 모우", List.of(
            new ChatAiRequest.Message(SenderRole.USER, "오늘 면접이 있었어요."),
            new ChatAiRequest.Message(SenderRole.ASSISTANT, "어땠어요?"),
            new ChatAiRequest.Message(SenderRole.USER, "너무 떨렸어요.")));

    @Test
    void 캐릭터_정보는_지시문에_대화는_입력에_담아_답변을_돌려준다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\" 많이 떨렸겠어요. \"}"));

        assertThat(client.reply(REQUEST)).isEqualTo("많이 떨렸겠어요.");

        var prompt = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(prompt.capture());
        assertThat(prompt.getValue().instructions()).contains("다정한 모우", "따뜻하게 들어 주는 모우");
        assertThat(prompt.getValue().input())
                .contains("오늘 면접이 있었어요.", "너무 떨렸어요.", "senderRole", "ASSISTANT")
                .doesNotContain("다정한 모우", "sessionId", "userId", "createdAt");
        assertThat(prompt.getValue().schema()).containsEntry("additionalProperties", false)
                .containsEntry("required", List.of("reply"));
    }

    @Test
    void 캐릭터_설명이_없어도_답변을_만든다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\"그랬군요.\"}"));

        assertThat(client.reply(new ChatAiRequest("모우", null, REQUEST.messages()))).isEqualTo("그랬군요.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"reply\":\"\"}",
            "{\"reply\":\"   \"}",
            "{\"reply\":123}",
            "{}",
            "{\"reply\":\"답변\",\"extra\":true}",
            "[\"답변\"]",
            "답변"
    })
    void 형식이_다른_응답은_실패로_본다(String content) {
        when(solar.complete(any())).thenReturn(result(content));

        assertThatThrownBy(() -> client.reply(REQUEST)).isInstanceOf(ChatAiException.class);
    }

    @Test
    void Solar_예외는_ChatAiException으로_바꾼다() {
        when(solar.complete(any())).thenThrow(new SolarApiException(SolarApiException.Kind.RATE_LIMIT));

        assertThatThrownBy(() -> client.reply(REQUEST))
                .isInstanceOf(ChatAiException.class)
                .hasCauseInstanceOf(SolarApiException.class);
    }

    @Test
    void 메시지가_없거나_비어_있으면_AI를_부르지_않는다() {
        assertThatThrownBy(() -> client.reply(new ChatAiRequest("모우", null, List.of())))
                .isInstanceOf(ChatAiException.class);
        assertThatThrownBy(() -> client.reply(new ChatAiRequest("모우", null,
                List.of(new ChatAiRequest.Message(SenderRole.USER, " ")))))
                .isInstanceOf(ChatAiException.class);
        verifyNoInteractions(solar);
    }

    private static SolarChatClient.Result result(String content) {
        return new SolarChatClient.Result(content, null);
    }
}
