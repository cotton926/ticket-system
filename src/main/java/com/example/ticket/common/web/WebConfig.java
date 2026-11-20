package com.example.ticket.common.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 登录接口、幂等令牌下发、健康检查、接口文档不需要登录态
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/error",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/doc.html",
                        "/webjars/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 本地前后端分离调试用；生产由 Nginx 统一处理跨域
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 查询对象里有一批"只允许服务端填"的字段：归属人、数据权限片段、时间区间、偏移量。
     *
     * <p>{@code @JsonIgnore} 只挡 JSON 反序列化，**挡不住 GET 的查询参数绑定** ——
     * 攻击者可以用 {@code ?dataScope=xxx} 尝试把片段塞进 SQL（该片段最终以 ${} 拼进语句）。
     * 虽然有 @DataScope 切面覆写兜底，但这里再加一道禁止绑定，做到纵深防御。</p>
     */
    @InitBinder("ticketQueryReq")
    public void ticketQueryBinder(WebDataBinder binder) {
        binder.setDisallowedFields("dataScope", "creatorId", "assigneeId", "offset",
                "startTime", "endTime");
    }
}
