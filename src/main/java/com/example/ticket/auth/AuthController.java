package com.example.ticket.auth;

import com.example.ticket.auth.dto.LoginReq;
import com.example.ticket.auth.dto.LoginResp;
import com.example.ticket.common.api.Result;
import com.example.ticket.common.web.AuthInterceptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResp> login(@RequestBody @Valid LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @Operation(summary = "当前用户信息与权限")
    @GetMapping("/me")
    public Result<LoginResp> me() {
        return Result.ok(authService.me());
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader(AuthInterceptor.TOKEN_HEADER);
        if (header != null && header.startsWith("Bearer ")) {
            authService.logout(header.substring(7).trim());
        }
        return Result.ok();
    }
}
