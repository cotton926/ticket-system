package com.example.ticket.system.controller;

import com.example.ticket.common.api.Result;
import com.example.ticket.system.mapper.SysUserMapper;
import com.example.ticket.system.vo.UserOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;

    /**
     * 可分派人员列表，供"分派/转派"弹窗的选择器使用。
     *
     * <p>不挂 @RequirePerm：处理人也要能转派，而处理人只有 ticket:handle；
     * 若收紧到 ticket:assign，李工打开转派弹窗时下拉会是空的。
     * 接口只返回 id/姓名/部门/角色，不含密码与联系方式，登录即可访问是可接受的暴露面。</p>
     */
    @Operation(summary = "可分派人员列表")
    @GetMapping("/handlers")
    public Result<List<UserOptionVO>> handlers() {
        return Result.ok(userMapper.selectAssignable());
    }
}
