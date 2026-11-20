package com.example.ticket.system.mapper;

import com.example.ticket.system.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysDeptMapper {

    @Select("SELECT * FROM sys_dept WHERE id = #{id}")
    SysDept selectById(@Param("id") Long id);

    @Select("SELECT name FROM sys_dept WHERE id = #{id}")
    String selectNameById(@Param("id") Long id);

    /**
     * 取本部门及所有子部门 ID。
     * <p>用 ancestors 路径 + FIND_IN_SET 一次查完，避免递归 CTE 或应用层循环 —
     * 组织架构一般不超过 5 层，扁平化的路径字段是最划算的反范式。</p>
     */
    @Select("SELECT id FROM sys_dept WHERE id = #{deptId} OR FIND_IN_SET(#{deptId}, ancestors)")
    java.util.List<Long> selectSelfAndChildIds(@Param("deptId") Long deptId);
}
