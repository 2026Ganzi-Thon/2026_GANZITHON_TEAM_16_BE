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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SolarChatAiClientTest {
    private final SolarChatClient solar = mock(SolarChatClient.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final SolarChatAiClient client = new SolarChatAiClient(solar, json);

    private static final ChatAiRequest REQUEST = new ChatAiRequest("KIND", "다정한 모우", "따뜻하게 들어 주는 모우", List.of(
            new ChatAiRequest.Message(SenderRole.USER, "오늘 면접이 있었어요."),
            new ChatAiRequest.Message(SenderRole.ASSISTANT, "어땠어요?"),
            new ChatAiRequest.Message(SenderRole.USER, "너무 떨렸어요.")));

    @Test
    void 캐릭터_정보는_지시문에_대화는_입력에_담아_답변을_돌려준다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\" 많이 떨렸겠어요. \"}"));

        assertThat(client.reply(REQUEST)).isEqualTo("많이 떨렸겠어요.");

        var prompt = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(prompt.capture());
        assertThat(prompt.getValue().instructions())
                .contains("다정한 모우", "따뜻하게 들어 주는 모우", "말투: 반말", "109(자살예방상담전화)");
        assertThat(prompt.getValue().input())
                .contains("오늘 면접이 있었어요.", "너무 떨렸어요.", "senderRole", "ASSISTANT")
                .doesNotContain("다정한 모우", "KIND", "sessionId", "userId", "createdAt");
        assertThat(prompt.getValue().schema()).containsEntry("additionalProperties", false)
                .containsEntry("required", List.of("reply"));
        // 답변은 측정상 낮춰도 나아지지 않아 모델 기본값을 쓴다.
        assertThat(prompt.getValue().temperature()).isNull();
    }

    @Test
    void 긴_대화는_최근_20개_메시지만_메시지마다_앞부분만_보낸다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\"그랬구나.\"}"));
        List<ChatAiRequest.Message> messages = IntStream.range(0, 25)
                .mapToObj(i -> new ChatAiRequest.Message(i % 2 == 0 ? SenderRole.USER : SenderRole.ASSISTANT,
                        i == 24 ? "가".repeat(1500) : "메시지 " + i))
                .toList();

        client.reply(new ChatAiRequest("KIND", "다정한 모우", null, messages));

        var prompt = ArgumentCaptor.forClass(SolarChatClient.Prompt.class);
        verify(solar).complete(prompt.capture());
        var sent = json.readTree(prompt.getValue().input()).path("messages");
        assertThat(sent).hasSize(20);
        assertThat(sent.get(0).path("content").asString()).isEqualTo("메시지 5");
        assertThat(sent.get(19).path("senderRole").asString()).isEqualTo("USER");
        assertThat(sent.get(19).path("content").asString()).hasSize(1000);
    }

    @Test
    void 지나치게_긴_답변은_실패로_본다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\"" + "가".repeat(1001) + "\"}"));

        assertThatThrownBy(() -> client.reply(REQUEST)).isInstanceOf(ChatAiException.class)
                .extracting(e -> ((ChatAiException) e).reason()).isEqualTo("INVALID_OUTPUT");
    }

    @Test
    void 형식이_깨진_응답은_한_번만_다시_요청한다() {
        when(solar.complete(any())).thenReturn(result("답변"), result("{\"reply\":\"그랬구나.\"}"));

        assertThat(client.reply(REQUEST)).isEqualTo("그랬구나.");
        verify(solar, times(2)).complete(any());
    }

    @Test
    void Solar가_형식_오류를_알리면_한_번만_다시_요청한다() {
        when(solar.complete(any()))
                .thenThrow(new SolarApiException(SolarApiException.Kind.INVALID_RESPONSE))
                .thenReturn(result("{\"reply\":\"그랬구나.\"}"));

        assertThat(client.reply(REQUEST)).isEqualTo("그랬구나.");
        verify(solar, times(2)).complete(any());
    }

    @Test
    void 두_번_모두_형식이_깨지면_실패한다() {
        when(solar.complete(any())).thenThrow(new SolarApiException(SolarApiException.Kind.INVALID_RESPONSE));

        assertThatThrownBy(() -> client.reply(REQUEST)).isInstanceOf(ChatAiException.class)
                .extracting(e -> ((ChatAiException) e).reason()).isEqualTo("INVALID_RESPONSE");
        verify(solar, times(2)).complete(any());
    }

    @Test
    void 길이_기준을_크게_넘으면_다시_만들고_다시_넘으면_그대로_받는다() {
        String longReply = "가".repeat(301);
        when(solar.complete(any())).thenReturn(result("{\"reply\":\"" + longReply + "\"}"),
                result("{\"reply\":\"짧은 답이야.\"}"));
        assertThat(client.reply(REQUEST)).isEqualTo("짧은 답이야.");

        when(solar.complete(any())).thenReturn(result("{\"reply\":\"" + longReply + "\"}"));
        assertThat(client.reply(REQUEST)).isEqualTo(longReply);
        verify(solar, times(4)).complete(any());
    }

    @Test
    void 위험_표현이_있는데_109_안내가_없으면_캐릭터_말투로_덧붙인다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\"많이 힘들구나.\"}"));
        List<ChatAiRequest.Message> risky = List.of(new ChatAiRequest.Message(SenderRole.USER, "요즘 그냥 사라지고 싶어."));

        assertThat(client.reply(new ChatAiRequest("KIND", "다정한 모우", null, risky)))
                .isEqualTo("많이 힘들구나. " + ChatPrompts.CRISIS_LINE_CASUAL);
        assertThat(client.reply(new ChatAiRequest("COOL", "냉철한 모우", null, risky)))
                .isEqualTo("많이 힘들구나. " + ChatPrompts.CRISIS_LINE_POLITE);
    }

    @Test
    void 캐릭터_설명이_없어도_답변을_만든다() {
        when(solar.complete(any())).thenReturn(result("{\"reply\":\"그랬군요.\"}"));

        assertThat(client.reply(new ChatAiRequest(null, "모우", null, REQUEST.messages()))).isEqualTo("그랬군요.");
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
    void Solar_예외는_다시_요청하지_않고_ChatAiException으로_바꾼다() {
        when(solar.complete(any())).thenThrow(new SolarApiException(SolarApiException.Kind.RATE_LIMIT));

        assertThatThrownBy(() -> client.reply(REQUEST))
                .isInstanceOf(ChatAiException.class)
                .hasCauseInstanceOf(SolarApiException.class)
                .extracting(e -> ((ChatAiException) e).reason()).isEqualTo("RATE_LIMIT");
        verify(solar, times(1)).complete(any());
    }

    @Test
    void 메시지가_없거나_비어_있으면_AI를_부르지_않는다() {
        assertThatThrownBy(() -> client.reply(new ChatAiRequest(null, "모우", null, List.of())))
                .isInstanceOf(ChatAiException.class);
        assertThatThrownBy(() -> client.reply(new ChatAiRequest(null, "모우", null,
                List.of(new ChatAiRequest.Message(SenderRole.USER, " ")))))
                .isInstanceOf(ChatAiException.class);
        verifyNoInteractions(solar);
    }

    private static SolarChatClient.Result result(String content) {
        return new SolarChatClient.Result(content, null);
    }
}
