package com.mowoo.mowoo_backend.chat.dto;

import com.mowoo.mowoo_backend.chat.dto.request.CreateDiaryReferenceRequest;
import com.mowoo.mowoo_backend.chat.dto.request.SendMessageRequest;
import com.mowoo.mowoo_backend.chat.dto.request.StartChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateChatSessionRequest;
import com.mowoo.mowoo_backend.chat.dto.request.UpdateMessageRequest;
import com.mowoo.mowoo_backend.global.request.CursorRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void startRequiresCharacterId() {
        assertThat(validator.validate(new StartChatSessionRequest(null))).hasSize(1);
        assertThat(validator.validate(new StartChatSessionRequest(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void endRequiresAction() {
        assertThat(validator.validate(new UpdateChatSessionRequest(null))).hasSize(1);
    }

    @Test
    void updatedMessageMustNotBeBlank() {
        assertThat(validator.validate(new UpdateMessageRequest(" "))).hasSize(1);
        assertThat(validator.validate(new UpdateMessageRequest("오늘 발표가 걱정돼요."))).isEmpty();
    }

    @Test
    void diaryReferenceRequiresMessageAndDiary() {
        assertThat(validator.validate(new CreateDiaryReferenceRequest(null, null))).hasSize(2);
    }

    @Test
    void blankContentIsTreatedAsEmpty() {
        assertThat(new SendMessageRequest(null, null).hasContent()).isFalse();
        assertThat(new SendMessageRequest("  ", null).hasContent()).isFalse();
    }

    @Test
    void cursorLimitDefaultsTo20() {
        assertThat(new CursorRequest(null, null).limit()).isEqualTo(CursorRequest.DEFAULT_LIMIT);
    }

    @Test
    void cursorLimitMustBeBetween1And100() {
        assertThat(validator.validate(new CursorRequest(0, null))).hasSize(1);
        assertThat(validator.validate(new CursorRequest(101, null))).hasSize(1);
        assertThat(validator.validate(new CursorRequest(100, "opaque_cursor"))).isEmpty();
    }
}
