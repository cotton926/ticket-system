package com.example.ticket.system.mapper;

import com.example.ticket.system.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    SysUser selectByUsername(@Param("username") String username);

    /** 处理人必须是存在且启用的账号，否则分派出去就是脏数据 */
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND status = 1 AND deleted = 0")
    SysUser selectEnabledById(@Param("id") Long id);

    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0")
    SysUser selectById(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM sys_user WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    @Insert("""
            INSERT INTO sys_user (username, password, real_name, dept_id, phone, email, status)
            VALUES (#{username}, #{password}, #{realName}, #{deptId}, #{phone}, #{email}, 1)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);

    @Select("""
            SELECT r.id FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 1
            """)
    java.util.List<Long> selectRoleIds(@Param("userId") Long userId);

    /**
     * 批量取姓名，用于列表页把 creatorId/assigneeId 翻译成人名。
     * <p>用"1 次 IN 查询"而不是在列表 SQL 里 join 两次 sys_user：
     * 大表查询 join 小表虽然很快，但会让索引覆盖变难，且分页时 join 成本随页深放大。
     * 这也是避免 N+1 的标准做法（一次批量取，而不是循环单查）。</p>
     */
    @Select("""
            <script>
            SELECT id, real_name FROM sys_user
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    java.util.List<SysUser> selectNamesByIds(@Param("ids") java.util.Collection<Long> ids);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int bindRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 可分派人员列表（分派、转派弹窗用）。
     *
     * <p>只返回具备处理能力的角色：处理人/调度/管理员。员工角色分派过去没人能接单，
     * 前端下拉里出现这类人只会让调度点错。</p>
     *
     * <p>用 GROUP BY 而不是 DISTINCT：一个人可能同时挂多个角色，DISTINCT 对
     * join 后的整行去重，两个角色会出两行；按主键分组才保证一人一条。</p>
     */
    @Select("""
            SELECT u.id, u.username, u.real_name, u.dept_id, d.name AS dept_name,
                   MIN(r.role_code) AS role_code
            FROM sys_user u
            LEFT JOIN sys_dept d ON d.id = u.dept_id
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.status = 1 AND u.deleted = 0 AND r.status = 1
              AND r.role_code IN ('HANDLER', 'DISPATCHER', 'ADMIN')
            GROUP BY u.id, u.username, u.real_name, u.dept_id, d.name
            ORDER BY FIELD(MIN(r.role_code), 'HANDLER', 'DISPATCHER', 'ADMIN'), u.id
            """)
    java.util.List<com.example.ticket.system.vo.UserOptionVO> selectAssignable();
}
