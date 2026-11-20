package com.example.ticket.common.aspect;

import com.example.ticket.common.annotation.DataScope;
import com.example.ticket.common.context.LoginUser;
import com.example.ticket.common.context.UserContext;
import com.example.ticket.common.scope.ScopedQuery;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面：把"当前用户能看到哪些数据"翻译成 SQL 片段注入查询对象。
 */
@Slf4j
@Aspect
@Component
public class DataScopeAspect {

    @Before("@annotation(dataScope)")
    public void before(JoinPoint pjp, DataScope dataScope) {
        LoginUser user = UserContext.require();
        String fragment = buildFragment(user, dataScope.alias());
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof ScopedQuery sq) {
                sq.setDataScope(fragment);
            }
        }
        log.debug("dataScope applied, user={}, fragment={}", user.getUsername(), fragment);
    }

    /**
     * 生成片段。所有值都来自 Long 类型，不含用户输入，故可安全用 ${} 拼接。
     */
    String buildFragment(LoginUser user, String alias) {
        int scope = user.getDataScope() == null ? 1 : user.getDataScope();
        Long uid = user.getId();
        Long deptId = user.getDeptId();
        return switch (scope) {
            // 本人：自己提交的 + 派给自己的
            case 1 -> " AND (" + alias + ".creator_id = " + uid
                    + " OR " + alias + ".assignee_id = " + uid + ") ";
            // 本部门
            case 2 -> " AND " + alias + ".creator_dept = " + deptId + " ";
            // 本部门及子部门：用 ancestors 路径前缀匹配，避免递归 CTE
            case 3 -> " AND " + alias + ".creator_dept IN (SELECT id FROM sys_dept WHERE id = "
                    + deptId + " OR FIND_IN_SET(" + deptId + ", ancestors)) ";
            // 全部
            default -> "";
        };
    }
}
