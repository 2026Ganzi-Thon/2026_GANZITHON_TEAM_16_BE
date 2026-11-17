package com.mowoo.mowoo_backend.chat.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPromptsTest {

    @Test
    void 캐릭터_코드별로_말투와_성격을_정한다() {
        assertThat(ChatPrompts.persona("KIND", "다정한 모우", "다정하게 이야기를 들어주는 모우"))
                .contains("\"다정한 모우\"", "캐릭터 설명: 다정하게 이야기를 들어주는 모우", "말투: 반말", "성격:", "말투 참고 예시");
        assertThat(ChatPrompts.persona("COOL", "냉철한 모우", null)).contains("캐릭터 설명: 없음", "말투: 존댓말", "성격:");
        assertThat(ChatPrompts.persona("CHEERFUL", "유쾌한 모우", null)).contains("말투: 반말", "성격:");
    }

    @Test
    void 가이드가_없는_코드는_캐릭터_설명을_따르고_존댓말로_고정한다() {
        assertThat(ChatPrompts.persona("PREMIUM", "새 모우", "새로 나온 모우"))
                .contains("\"새 모우\"", "캐릭터 설명: 새로 나온 모우", "말투: 존댓말").doesNotContain("성격:");
        assertThat(ChatPrompts.persona(null, "모우", null)).contains("말투: 존댓말").doesNotContain("성격:");
    }

    @Test
    void 위기_안내는_스스로를_해칠_생각이_드러날_때로_한정한다() {
        assertThat(ChatPrompts.CRISIS).contains("109(자살예방상담전화)", "반드시", "109를 말하지 않는다");
    }

    @Test
    void 위험_표현이_있고_109_안내가_없을_때만_안내_문장을_덧붙인다() {
        assertThat(ChatPrompts.withCrisisLine("힘들구나.", "요즘 죽고 싶다는 생각이 들어", "KIND"))
                .isEqualTo("힘들구나. " + ChatPrompts.CRISIS_LINE_CASUAL);
        assertThat(ChatPrompts.withCrisisLine("힘드시겠어요.", "내가 없어지면 다들 편하겠지", "COOL"))
                .isEqualTo("힘드시겠어요. " + ChatPrompts.CRISIS_LINE_POLITE);
        assertThat(ChatPrompts.withCrisisLine("힘드시겠어요.", "그냥 잠들었다가 안 깼으면", null))
                .isEqualTo("힘드시겠어요. " + ChatPrompts.CRISIS_LINE_POLITE);
        assertThat(ChatPrompts.withCrisisLine("109에 전화해 봐.", "죽고 싶어", "KIND")).isEqualTo("109에 전화해 봐.");
        assertThat(ChatPrompts.withCrisisLine("고생했어.", null, "KIND")).isEqualTo("고생했어.");
    }

    @Test
    void 흔한_과장_표현에는_안내_문장을_덧붙이지_않는다() {
        assertThat(ChatPrompts.withCrisisLine("고생 많았다.", "과제 너무 많아서 죽겠어. 빨리 끝내고 싶다.", "KIND")).isEqualTo("고생 많았다.");
        assertThat(ChatPrompts.withCrisisLine("배고프겠다.", "배고파 죽겠어", "KIND")).isEqualTo("배고프겠다.");
    }

    @Test
    void 글자_수나_문장_수_기준을_넘었는지_본다() {
        assertThat(ChatPrompts.exceeds("가".repeat(301), 300, 5)).isTrue();
        assertThat(ChatPrompts.exceeds("하나. 둘. 셋. 넷. 다섯. 여섯.", 300, 5)).isTrue();
        assertThat(ChatPrompts.exceeds("하나. 둘? 셋!", 300, 5)).isFalse();
    }

    @Test
    void 긴_내용은_앞부분만_남긴다() {
        assertThat(ChatPrompts.truncate("가나다", 2)).isEqualTo("가나");
        assertThat(ChatPrompts.truncate("가나", 2)).isEqualTo("가나");
    }
}
