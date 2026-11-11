package com.mowoo.mowoo_backend.diary.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;

// 생략은 기존 값 유지, 명시적인 null은 잘못된 요청으로 구분한다.
@Getter
public class UpdateDiaryRequest {

    @Size(max = 150)
    private String title;
    private String content;
    private LocalDate entryDate;
    private Boolean save;

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title은 null일 수 없습니다.");
    }

    @JsonSetter("content")
    public void setContent(String content) {
        this.content = Objects.requireNonNull(content, "content는 null일 수 없습니다.");
    }

    @JsonSetter("entryDate")
    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = Objects.requireNonNull(entryDate, "entryDate는 null일 수 없습니다.");
    }

    @JsonSetter("save")
    public void setSave(Boolean save) {
        this.save = Objects.requireNonNull(save, "save는 null일 수 없습니다.");
    }

    @AssertTrue
    @JsonIgnore
    public boolean isTitleValid() {
        return title == null || !title.isBlank();
    }

    @AssertTrue
    @JsonIgnore
    public boolean isContentValid() {
        return content == null || (!content.isBlank()
                && content.getBytes(StandardCharsets.UTF_8).length <= 65535);
    }

    @AssertTrue
    @JsonIgnore
    public boolean isEntryDateWithinStorageRange() {
        return entryDate == null || (entryDate.getYear() >= 1000 && entryDate.getYear() <= 9999);
    }
}
