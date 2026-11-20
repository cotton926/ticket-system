package com.example.ticket.system.mapper;

import com.example.ticket.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper {

    @Select("""
            SELECT r.* FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 1
            """)
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM sys_role WHERE role_code = #{roleCode} AND status = 1")
    SysRole selectByCode(@Param("roleCode") String roleCode);

    @Select("SELECT * FROM sys_role WHERE id = #{id}")
    SysRole selectById(@Param("id") Long id);
}
