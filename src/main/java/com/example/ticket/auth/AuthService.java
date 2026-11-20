package com.example.ticket.auth;

import com.example.ticket.auth.dto.LoginReq;
import com.example.ticket.auth.dto.LoginResp;
import com.example.ticket.common.context.LoginUser;
import com.example.ticket.common.context.UserContext;
import com.example.ticket.common.exception.BizException;
import com.example.ticket.common.exception.ErrorCode;
import com.example.ticket.system.entity.SysRole;
import com.example.ticket.system.entity.SysUser;
import com.example.ticket.system.mapper.SysDeptMapper;
import com.example.ticket.system.mapper.SysPermissionMapper;
import com.example.ticket.system.mapper.SysRoleMapper;
import com.example.ticket.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysDeptMapper deptMapper;
    private final SessionStore sessionStore;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.token-ttl-minutes:120}")
    private long tokenTtlMinutes;

    public LoginResp login(LoginReq req) {
        SysUser user = userMapper.selectByUsername(req.getUsername());
        // 用户不存在与密码错误返回同一个错误码：否则就成了账号枚举接口
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("login failed, username={}", req.getUsername());
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        LoginUser loginUser = buildLoginUser(user);
        String token = UUID.randomUUID().toString().replace("-", "");
        sessionStore.save(token, loginUser, tokenTtlMinutes);

        log.info("login ok, user={}, roles={}, dataScope={}",
                user.getUsername(), loginUser.getRoles(), loginUser.getDataScope());

        return LoginResp.builder()
                .token(token)
                .userId(loginUser.getId())
                .username(loginUser.getUsername())
                .realName(loginUser.getRealName())
                .deptId(loginUser.getDeptId())
                .deptName(loginUser.getDeptName())
                .dataScope(loginUser.getDataScope())
                .roles(loginUser.getRoles())
                .perms(loginUser.getPerms())
                .build();
    }

    public LoginResp me() {
        LoginUser u = UserContext.require();
        return LoginResp.builder()
                .userId(u.getId())
                .username(u.getUsername())
                .realName(u.getRealName())
                .deptId(u.getDeptId())
                .deptName(u.getDeptName())
                .dataScope(u.getDataScope())
                .roles(u.getRoles())
                .perms(u.getPerms())
                .build();
    }

    /** 登录态在登录时一次性算好：每次请求都查 3 张权限表会把 QPS 天花板压得很低 */
    private LoginUser buildLoginUser(SysUser user) {
        List<SysRole> roles = roleMapper.selectByUserId(user.getId());
        Set<String> roleCodes = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
        // 多角色取最大数据范围：一个员工同时是处理人和调度时，应该按调度看本部门
        int dataScope = roles.stream()
                .map(r -> r.getDataScope() == null ? 1 : r.getDataScope())
                .max(Integer::compareTo).orElse(1);

        Set<String> perms = new HashSet<>(permissionMapper.selectPermCodesByUserId(user.getId()));

        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRealName(user.getRealName());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setDeptName(deptMapper.selectNameById(user.getDeptId()));
        loginUser.setDataScope(dataScope);
        loginUser.setRoles(roleCodes);
        loginUser.setPerms(perms);
        return loginUser;
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionStore.remove(token);
        }
    }
}
