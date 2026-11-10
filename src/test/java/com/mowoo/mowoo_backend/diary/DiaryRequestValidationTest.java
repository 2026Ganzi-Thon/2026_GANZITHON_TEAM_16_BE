package com.mowoo.mowoo_backend.diary;

import com.mowoo.mowoo_backend.diary.dto.request.CreateDiaryRequest;
import com.mowoo.mowoo_backend.diary.dto.request.UpdateDiaryRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DiaryRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void requiresDateTitleAndContent() {
        assertThat(validator.validate(new CreateDiaryRequest(null, null, null))).hasSize(3);
        assertThat(validator.validate(request(" ", " "))).hasSize(2);
    }

    @Test
    void titleAllows150CharactersButNot151() {
        assertThat(validator.validate(request("a".repeat(150), "text"))).isEmpty();
        assertThat(validator.validate(request("a".repeat(151), "text"))).isNotEmpty();
    }

    @Test
    void textLimitIsBasedOnUtf8Bytes() {
        assertThat(validator.validate(request("title", "a".repeat(65535)))).isEmpty();
        assertThat(validator.validate(request("title", "a".repeat(65536)))).isNotEmpty();
        assertThat(validator.validate(request("title", "가".repeat(21845)))).isEmpty();
        assertThat(validator.validate(request("title", "가".repeat(21846)))).isNotEmpty();
        assertThat(validator.validate(request("title", "😀".repeat(16384)))).isNotEmpty();
    }

    @Test
    void datesMustFitMysqlDateStorageRange() {
        assertThat(validator.validate(new CreateDiaryRequest(LocalDate.of(1, 1, 1), "title", "text")))
                .isNotEmpty();
        assertThat(validator.validate(new CreateDiaryRequest(LocalDate.of(1000, 1, 1), "title", "text")))
                .isEmpty();
        assertThat(validator.validate(new CreateDiaryRequest(LocalDate.of(9999, 12, 31), "title", "text")))
                .isEmpty();
        UpdateDiaryRequest update = new UpdateDiaryRequest();
        update.setEntryDate(LocalDate.of(10000, 1, 1));
        assertThat(validator.validate(update)).isNotEmpty();
    }

    @Test
    void omittedPatchFieldsAreValidButBlankOrOversizedValuesAreNot() {
        UpdateDiaryRequest update = new UpdateDiaryRequest();
        assertThat(validator.validate(update)).isEmpty();
        update.setTitle(" ");
        assertThat(validator.validate(update)).isNotEmpty();
        update.setTitle("a".repeat(151));
        assertThat(validator.validate(update)).isNotEmpty();
        update.setTitle("title");
        update.setContent("😀".repeat(16384));
        assertThat(validator.validate(update)).isNotEmpty();
    }

    private CreateDiaryRequest request(String title, String content) {
        return new CreateDiaryRequest(LocalDate.of(2026, 11, 11), title, content);
    }
}
