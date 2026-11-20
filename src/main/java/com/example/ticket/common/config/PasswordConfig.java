package com.example.ticket.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 只引入 spring-security-crypto 拿 BCrypt，不引入整个 Spring Security。
 * <p>本项目只需要"密码哈希 + 校验"这一件事，整套过滤器链和自动配置是净负担。</p>
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength=10（默认）：单次校验约 60~80ms，足够挡住离线爆破又不至于拖慢登录
        return new BCryptPasswordEncoder(10);
    }
}
