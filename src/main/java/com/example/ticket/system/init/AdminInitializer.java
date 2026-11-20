package com.example.ticket.system.init;

import com.example.ticket.system.entity.SysUser;
import com.example.ticket.system.mapper.SysRoleMapper;
import com.example.ticket.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 演示账号初始化。
 *
 * <p><b>为什么不在 SQL 里写死密码哈希</b>：BCrypt 哈希手写无法自校验，一旦抄错就会出现
 * "文档写着 123456 却登不进去"的诡异问题，而且不同轮次的 salt 也不同。放到启动时用
 * 同一个 PasswordEncoder 生成，保证"文档怎么写、程序就能怎么登"。</p>
 *
 * <p>幂等：按 username 存在性判断，重复启动不会重复插入。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    private record Seed(String username, String realName, long deptId, String roleCode) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("admin", "系统管理员", 1L, "ADMIN"),
            new Seed("dispatcher", "王调度", 2L, "DISPATCHER"),
            new Seed("handler", "李工", 4L, "HANDLER"),
            new Seed("employee", "张三", 3L, "EMPLOYEE"),
            new Seed("employee2", "赵四", 2L, "EMPLOYEE")
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        for (Seed seed : SEEDS) {
            if (userMapper.countByUsername(seed.username()) > 0) {
                continue;
            }
            SysUser user = new SysUser();
            user.setUsername(seed.username());
            user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
            user.setRealName(seed.realName());
            user.setDeptId(seed.deptId());
            user.setEmail(seed.username() + "@example.com");
            userMapper.insert(user);

            Long roleId = roleMapper.selectByCode(seed.roleCode()).getId();
            userMapper.bindRole(user.getId(), roleId);
            log.info("seed user created: {} / {} (role={}, dept={})",
                    seed.username(), DEFAULT_PASSWORD, seed.roleCode(), seed.deptId());
        }
    }
}
