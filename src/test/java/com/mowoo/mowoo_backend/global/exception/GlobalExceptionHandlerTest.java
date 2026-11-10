package com.mowoo.mowoo_backend.global.exception;

import com.mowoo.mowoo_backend.global.code.BaseCode;
import com.mowoo.mowoo_backend.global.config.JwtConfig;
import com.mowoo.mowoo_backend.global.config.SecurityConfig;
import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({
        GlobalExceptionHandlerTest.TestController.class,
        SecurityConfig.class,
        JwtConfig.class,
        SecurityErrorHandler.class
})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-key-for-mowoo-backend-0123456789")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successResponseReturnsDtoWithoutWrapper() throws Exception {
        mockMvc.perform(get("/test/ok").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("모우"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("요청이 처리됐어요."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void businessExceptionUsesStatusAndCodeOfErrorCode() throws Exception {
        mockMvc.perform(get("/test/business").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.message").value("대화를 찾을 수 없어요."))
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("대화를 찾을 수 없어요."));
    }

    @Test
    void invalidRequestBodyReturnsValidationError() throws Exception {
        mockMvc.perform(post("/test/validation").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("요청 값을 확인해 주세요."));
    }

    @Test
    void malformedJsonReturnsValidationError() throws Exception {
        mockMvc.perform(post("/test/validation").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/test/unknown").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/test/ok").with(jwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void accessDeniedReturnsForbidden() throws Exception {
        mockMvc.perform(get("/test/forbidden").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void unexpectedExceptionReturnsInternalError() throws Exception {
        mockMvc.perform(get("/test/unexpected").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.message").value("로그인이 필요해요."))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("로그인이 필요해요."));
    }

    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/ok").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/ok")
        TestResponse ok() {
            return new TestResponse(true, CommonSuccessCode.SUCCESS.name(), CommonSuccessCode.SUCCESS.getMessage(), "모우");
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(TestErrorCode.SESSION_NOT_FOUND);
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("접근 거부");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("예상하지 못한 오류");
        }
    }

    record TestRequest(@NotBlank String content) {
    }

    record TestResponse(boolean isSuccess, String code, String message, String name) {
    }

    @Getter
    @RequiredArgsConstructor
    enum TestErrorCode implements BaseCode {

        SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "대화를 찾을 수 없어요.");

        private final HttpStatus status;
        private final String message;
    }
}
