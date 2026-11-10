package com.mowoo.mowoo_backend.calendar.ai;
import java.util.List;
public interface MonthlyRecapAiClient {
    String summarize(List<String> diaryContents);
}
