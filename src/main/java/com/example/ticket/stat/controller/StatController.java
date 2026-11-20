package com.example.ticket.stat.controller;

import com.example.ticket.common.annotation.RequirePerm;
import com.example.ticket.common.api.Result;
import com.example.ticket.stat.service.StatService;
import com.example.ticket.stat.vo.StatOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "统计")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @Operation(summary = "统计概览（总量 / 今日新增 / 超时数 / 平均处理时长 / 状态分布 / 处理人负载）")
    @RequirePerm("stat:view")
    @GetMapping("/overview")
    public Result<StatOverviewVO> overview() {
        return Result.ok(statService.overview());
    }
}
