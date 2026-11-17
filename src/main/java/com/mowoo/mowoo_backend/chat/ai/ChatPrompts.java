package com.mowoo.mowoo_backend.chat.ai;

import java.util.Map;
import java.util.regex.Pattern;

// 캐릭터가 직접 말하는 프롬프트(메시지 답변, 과거 일기 비교 답변)가 함께 쓰는 지시문.
final class ChatPrompts {

    private record Voice(boolean casual, String personality, String example) {
    }

    private static final String CASUAL = "반말. 모든 문장을 반말로 쓰고 \"~요\", \"~습니다\"로 끝내지 않는다.";
    private static final String POLITE = "존댓말(해요체). 모든 문장을 \"~요\"로 끝내고 반말을 섞지 않는다.";

    // 코드는 기본 캐릭터 등록(DefaultCharacterService)과 맞춘다.
    private static final Map<String, Voice> VOICES = Map.of(
            "KIND", new Voice(true,
                    "감정을 먼저 알아주고 따뜻하게 천천히 묻는다.",
                    "면접을 앞두고 많이 불안했구나. 어떤 점이 제일 걱정돼?"),
            "COOL", new Voice(false,
                    "상황을 짧게 정리하고 핵심을 하나 묻는다. 감탄이나 과한 위로는 하지 않는다.",
                    "면접 준비와 남들과의 비교, 두 가지가 겹쳐 있네요. 지금 더 크게 느껴지는 건 어느 쪽인가요?"),
            "CHEERFUL", new Voice(true,
                    "밝고 경쾌하게 받아 주되 고민을 가볍게 넘기지 않는다. 걱정이나 슬픔 앞에서는 장난을 줄인다.",
                    "면접이라니, 떨릴 만하지! 제일 신경 쓰이는 게 뭐야?"));

    static final String CRISIS = """
            사용자가 죽고 싶다, 사라지고 싶다, 다 끝내고 싶다, 자해하고 싶다처럼 스스로를 해칠 수 있는 생각을 드러내면, 공감한 뒤 109(자살예방상담전화)나 믿을 수 있는 주변 사람에게 이야기해 보라고 반드시 권한다. 이때는 109 안내를 다른 질문보다 먼저 한다.
            이런 생각이 드러나지 않은 일상적인 걱정·긴장·슬픔에는 109를 말하지 않는다.""";

    // 스스로를 해칠 생각을 드러내는 표현. 돌려 말하는 표현은 널리 알려진 것만 넣고,
    // "배고파 죽겠어", "과제 끝내고 싶어" 같은 흔한 말은 넣지 않는다.
    private static final Pattern CRISIS_WORDS = Pattern.compile(
            "죽고\\s*싶|사라지고\\s*싶|없어지고\\s*싶|다\\s*끝내고\\s*싶|끝내\\s*버리고\\s*싶|살기\\s*싫|그만\\s*살고\\s*싶|자해|자살|목숨을\\s*끊"
                    + "|(없어지|사라지)면\\s*(다들|모두|다)?\\s*편|안\\s*깼으면|태어나지\\s*말");
    static final String CRISIS_LINE_CASUAL = "혼자 견디기 힘들면 109(자살예방상담전화)나 믿을 수 있는 사람에게 꼭 이야기해 줘.";
    static final String CRISIS_LINE_POLITE = "혼자 견디기 힘드시면 109(자살예방상담전화)나 믿을 수 있는 사람에게 꼭 이야기해 주세요.";
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+|\\n+");

    private ChatPrompts() {
    }

    // 프롬프트만으로는 109 안내가 가끔 빠지므로, 위험 표현이 있는데 답변에 109가 없으면 캐릭터 말투에 맞는 안내 문장을 덧붙인다.
    static String withCrisisLine(String reply, String userText, String code) {
        if (userText == null || reply.contains("109") || !CRISIS_WORDS.matcher(userText).find()) {
            return reply;
        }
        Voice voice = code == null ? null : VOICES.get(code);
        return reply + " " + (voice != null && voice.casual() ? CRISIS_LINE_CASUAL : CRISIS_LINE_POLITE);
    }

    // 프롬프트의 길이 기준을 크게 벗어났는지 본다. 다시 요청할지 정하는 데만 쓴다.
    static boolean exceeds(String text, int maxChars, int maxSentences) {
        return text.length() > maxChars
                || SENTENCE_END.splitAsStream(text.strip()).filter(s -> !s.isBlank()).count() > maxSentences;
    }

    // 가이드가 없는 코드(새로 추가된 캐릭터)는 캐릭터 설명을 따르고, 말투는 존댓말로 고정한다.
    static String persona(String code, String name, String description) {
        Voice voice = code == null ? null : VOICES.get(code);
        StringBuilder text = new StringBuilder()
                .append("너는 사용자의 하루 이야기를 들어 주는 캐릭터 \"").append(name).append("\"다.\n")
                .append("캐릭터 설명: ").append(description == null ? "없음" : description).append('\n');
        if (voice == null) {
            return text.append("말투: ").append(POLITE).append('\n').toString();
        }
        return text.append("말투: ").append(voice.casual() ? CASUAL : POLITE).append('\n')
                .append("성격: ").append(voice.personality()).append('\n')
                .append("말투 참고 예시(그대로 쓰지 않는다): ").append(voice.example()).append('\n')
                .toString();
    }

    static String truncate(String content, int maxChars) {
        return content.length() <= maxChars ? content : content.substring(0, maxChars);
    }
}
