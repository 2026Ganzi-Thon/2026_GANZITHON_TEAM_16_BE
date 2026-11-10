package com.mowoo.mowoo_backend.ai.solar;

import com.mowoo.mowoo_backend.chat.entity.SenderRole;
import com.mowoo.mowoo_backend.chat.service.ChatTranscript;
import com.mowoo.mowoo_backend.diary.ai.SolarDiaryAiClient;
import com.mowoo.mowoo_backend.emotion.ai.EmotionAiClient;
import com.mowoo.mowoo_backend.emotion.ai.SolarEmotionAiClient;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 유료 API다. 비용/2회 호출에 대한 사용자 승인 뒤 별도 프로세스 환경변수로만 실행한다.
// 일반 test에는 실행되지 않는다. 실제 사용자 데이터/DB/인증 토큰을 사용하지 않는다.
@Tag("paid-ai")
@EnabledIfEnvironmentVariable(named = "UPSTAGE_LIVE_TEST_APPROVED", matches = "true")
class SolarLiveSmokeTest {
    private SolarChatClient solar;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void configure() {
        Dotenv env;
        try { env = Dotenv.configure().ignoreIfMissing().load(); }
        catch (RuntimeException e) {
            throw new IllegalStateException("로컬 환경변수 파일을 읽지 못했습니다. 원문은 출력하지 않습니다.");
        }
        String key = env.get("UPSTAGE_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "로컬 API 키가 준비되지 않았습니다.");
        SolarProperties settings = new SolarProperties();
        settings.setApiKey(key);
        settings.setEnabled(true);
        settings.setModel(env.get("UPSTAGE_MODEL", "solar-pro4"));
        settings.setBaseUrl(env.get("UPSTAGE_BASE_URL", "https://api.upstage.ai/v1"));
        solar = new SolarChatClient(new SolarConfiguration().solarRestClient(settings), settings, json);
    }

    @Test
    void syntheticConversationProducesDiaryShape() {
        var transcript = new ChatTranscript(UUID.randomUUID(), Instant.now(), null, 1, false, List.of(
                new ChatTranscript.Message(1, SenderRole.USER, null, "오늘 공원에서 산책하고 책을 읽었다.", Instant.now()),
                new ChatTranscript.Message(2, SenderRole.ASSISTANT, "모우", "오늘의 이야기를 들었어요.", Instant.now())));
        var diary = new SolarDiaryAiClient(solar, json).generate(transcript);
        assertThat(diary.title()).isNotBlank().hasSizeLessThanOrEqualTo(150);
        assertThat(diary.content()).isNotBlank();
    }

    @Test
    void syntheticDiaryProducesAllowedEmotionCodes() {
        var result = new SolarEmotionAiClient(solar, json).suggest(
                new EmotionAiClient.DiaryText("산책한 날", "공원에서 산책해서 마음이 편안하고 즐거웠다."),
                List.of(new EmotionAiClient.Option("CALM", "평온한 날"),
                        new EmotionAiClient.Option("ANGRY", "화났던 날"),
                        new EmotionAiClient.Option("SAD", "우울한 날"),
                        new EmotionAiClient.Option("HAPPY", "즐거운 날")));
        assertThat(result.emotionCodes()).isNotEmpty().doesNotHaveDuplicates()
                .isSubsetOf("CALM", "ANGRY", "SAD", "HAPPY");
        assertThat(result.emotionCodes()).contains(result.primaryEmotionCode());
    }
}
