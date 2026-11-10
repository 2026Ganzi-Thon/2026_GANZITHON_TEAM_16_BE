package com.mowoo.mowoo_backend.emotion.ai;

import java.util.List;

public interface EmotionAiClient {
    record DiaryText(String title, String content) { }
    record Option(String code, String name) { }
    EmotionAiResult suggest(DiaryText diary, List<Option> allowedEmotions);
}
