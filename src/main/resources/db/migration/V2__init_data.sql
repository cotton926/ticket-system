-- ============================================================================
-- V2：初始化基础数据（部门 / 角色 / 权限点 / 工单分类）
-- 用户账号由 AdminInitializer 在启动时创建 —— 因为 BCrypt 哈希必须在运行时生成，
-- 手写进 SQL 的哈希无法验证，容易造成"文档写着 123456 却登不进去"。
-- ============================================================================

INSERT INTO `sys_dept` (`id`, `parent_id`, `name`, `ancestors`, `sort`) VALUES
  (1, 0, '总公司',     '0',     1),
  (2, 1, '信息技术部', '0,1',   1),
  (3, 1, '行政部',     '0,1',   2),
  (4, 2, '运维组',     '0,1,2', 1);

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `data_scope`) VALUES
  (1, 'EMPLOYEE',   '员工',   1),
  (2, 'HANDLER',    '处理人', 1),
  (3, 'DISPATCHER', '调度',   2),
  (4, 'ADMIN',      '管理员', 4);

INSERT INTO `sys_permission` (`id`, `perm_code`, `perm_name`, `perm_type`) VALUES
  (1, 'ticket:create', '提交工单', 3),
  (2, 'ticket:assign', '分派工单', 3),
  (3, 'ticket:handle', '处理工单', 3),
  (4, 'ticket:verify', '验收工单', 3),
  (5, 'ticket:close',  '强制关闭', 3),
  (6, 'stat:view',     '查看统计', 3);

-- 员工：只能提、只能验收自己的
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1, 1), (1, 4);
-- 处理人：能提、能处理
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (2, 1), (2, 3);
-- 调度：能提、能处理、能分派、能强制关闭、能看统计
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (3, 1), (3, 2), (3, 3), (3, 5), (3, 6);
-- 管理员：全部
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
  (4, 1), (4, 2), (4, 3), (4, 4), (4, 5), (4, 6);

INSERT INTO `ticket_type` (`id`, `name`, `parent_id`, `sla_hours`) VALUES
  (1, '系统故障',   0,  4),
  (2, '账号权限',   0,  8),
  (3, '网络问题',   0,  8),
  (4, '办公设备',   0, 24),
  (5, '行政事务',   0, 48);
