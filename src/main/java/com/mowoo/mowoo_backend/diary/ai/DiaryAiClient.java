package com.mowoo.mowoo_backend.diary.ai;

import com.mowoo.mowoo_backend.chat.service.ChatTranscript;

public interface DiaryAiClient {
    DiaryAiResult generate(ChatTranscript transcript);
}
