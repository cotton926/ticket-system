package com.example.ticket.common.controller;

import com.example.ticket.common.api.Result;
import com.example.ticket.common.idempotent.IdempotentTokenStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 幂等令牌下发。
 * <p>前端在"提交工单"页面加载时先取一次令牌，提交时带上 {@code X-Idempotent-Token}，
 * 令牌一次性消费。</p>
 */
@Tag(name = "幂等令牌")
@RestController
@RequestMapping("/api/v1/idempotent-token")
@RequiredArgsConstructor
public class IdempotentController {

    private final IdempotentTokenStore tokenStore;

    @Operation(summary = "获取幂等令牌（5 分钟内一次性有效）")
    @GetMapping
    public Result<Map<String, String>> issue() {
        return Result.ok(Map.of("token", tokenStore.issue()));
    }
}
