package com.mowoo.mowoo_backend.emotion.ai;
import java.util.List;
public record EmotionAiResult(List<String> emotionCodes, String primaryEmotionCode) { }
