package com.mowoo.mowoo_backend.global;

import com.mowoo.mowoo_backend.global.response.CommonSuccessCode;
import com.mowoo.mowoo_backend.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "health", description = "health check 관련 api 입니다.")
@RestController
public class RootController {
    @Operation(summary = "cicd 확인", description = "cicd가 정상적으로 작동하고 있는지 확인하는 메서드입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
    })
    @GetMapping("/health")
    public SuccessResponse healthCheck() {
        return SuccessResponse.of(CommonSuccessCode.HEALTH_CHECK_SUCCESS);
    }
}
