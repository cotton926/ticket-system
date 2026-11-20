-- ============================================================================
-- V1：初始化表结构（11 张表）
-- 约定：主键 BIGINT 自增、时间 DATETIME、枚举 TINYINT、字符集 utf8mb4
-- ============================================================================

-- ---------------------------------------------------------------- 组织与权限

CREATE TABLE `sys_dept` (
  `id`        BIGINT       NOT NULL AUTO_INCREMENT,
  `parent_id` BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID，0为根',
  `name`      VARCHAR(50)  NOT NULL COMMENT '部门名称',
  `ancestors` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖级路径，如 0,1,2；子部门查询用 FIND_IN_SET',
  `sort`      INT          NOT NULL DEFAULT 0,
  `status`    TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

CREATE TABLE `sys_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `username`    VARCHAR(50)  NOT NULL COMMENT '登录名',
  `password`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希，禁止明文/裸MD5',
  `real_name`   VARCHAR(50)  NOT NULL,
  `dept_id`     BIGINT       NOT NULL,
  `phone`       VARCHAR(64)      NULL COMMENT '加密存储',
  `email`       VARCHAR(100)     NULL,
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `sys_role` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `role_code`  VARCHAR(50) NOT NULL COMMENT 'EMPLOYEE/HANDLER/DISPATCHER/ADMIN',
  `role_name`  VARCHAR(50) NOT NULL,
  `data_scope` TINYINT     NOT NULL DEFAULT 1 COMMENT '1本人 2本部门 3本部门及子部门 4全部',
  `status`     TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE `sys_permission` (
  `id`        BIGINT       NOT NULL AUTO_INCREMENT,
  `parent_id` BIGINT       NOT NULL DEFAULT 0,
  `perm_code` VARCHAR(100) NOT NULL COMMENT '如 ticket:assign',
  `perm_name` VARCHAR(50)  NOT NULL,
  `perm_type` TINYINT      NOT NULL DEFAULT 3 COMMENT '1菜单 2按钮 3接口',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点表';

CREATE TABLE `sys_user_role` (
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色';

CREATE TABLE `sys_role_permission` (
  `role_id`       BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';

-- ------------------------------------------------------------------- 工单

CREATE TABLE `ticket` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `ticket_no`     VARCHAR(32)  NOT NULL COMMENT 'TK+yyyyMMdd+6位序列',
  `title`         VARCHAR(120) NOT NULL,
  `content`       TEXT         NOT NULL COMMENT '问题描述',
  `type_id`       INT          NOT NULL,
  `priority`      TINYINT      NOT NULL DEFAULT 3 COMMENT '1紧急 2高 3普通 4低',
  `status`        TINYINT      NOT NULL DEFAULT 10 COMMENT '状态机码值',
  `creator_id`    BIGINT       NOT NULL,
  `creator_dept`  BIGINT       NOT NULL COMMENT '冗余快照，列表数据权限免 join',
  `assignee_id`   BIGINT           NULL,
  `assignee_dept` BIGINT           NULL COMMENT '冗余快照',
  `assign_time`   DATETIME         NULL,
  `finish_time`   DATETIME         NULL,
  `close_time`    DATETIME         NULL,
  `deadline`      DATETIME         NULL COMMENT 'SLA 截止',
  `timeout_flag`  TINYINT      NOT NULL DEFAULT 0 COMMENT '1已标记超时',
  `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  KEY `idx_creator_ctime`         (`creator_id`, `create_time`),
  KEY `idx_assignee_status_ctime` (`assignee_id`, `status`, `create_time`),
  KEY `idx_status_dept_ctime`     (`status`, `creator_dept`, `create_time`),
  KEY `idx_ctime_status`          (`create_time`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单主表';

CREATE TABLE `ticket_flow_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `ticket_id`     BIGINT       NOT NULL,
  `action`        VARCHAR(32)  NOT NULL COMMENT 'SUBMIT/ASSIGN/ACCEPT/FINISH/VERIFY_PASS/...',
  `action_desc`   VARCHAR(50)  NOT NULL COMMENT '动作中文描述',
  `from_status`   TINYINT      NOT NULL,
  `to_status`     TINYINT      NOT NULL,
  `operator_id`   BIGINT       NOT NULL,
  `operator_name` VARCHAR(50)  NOT NULL COMMENT '冗余快照，追溯页免 join',
  `remark`        VARCHAR(500)     NULL,
  `create_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ticket_ctime` (`ticket_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单流转日志（状态追溯的唯一来源）';

CREATE TABLE `ticket_comment` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `ticket_id`   BIGINT        NOT NULL,
  `user_id`     BIGINT        NOT NULL,
  `user_name`   VARCHAR(50)   NOT NULL COMMENT '快照',
  `content`     VARCHAR(2000) NOT NULL,
  `is_internal` TINYINT       NOT NULL DEFAULT 0 COMMENT '1内部备注（提交人不可见）',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticket_ctime` (`ticket_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单评论';

CREATE TABLE `ticket_type` (
  `id`        INT         NOT NULL AUTO_INCREMENT,
  `name`      VARCHAR(50) NOT NULL,
  `parent_id` INT         NOT NULL DEFAULT 0,
  `sla_hours` INT         NOT NULL DEFAULT 24 COMMENT 'SLA 时长，用于算 deadline',
  `status`    TINYINT     NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单分类';

-- ------------------------------------------------------------------- 幂等

CREATE TABLE `idempotent_record` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `biz_type`     VARCHAR(32) NOT NULL COMMENT 'TICKET_SUBMIT / TICKET_ASSIGN',
  `biz_key`      VARCHAR(64) NOT NULL,
  `request_hash` CHAR(64)        NULL COMMENT '请求参数 SHA256，用于识别"同键不同参"',
  `result_code`  INT             NULL,
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz` (`biz_type`, `biz_key`),
  KEY `idx_ctime` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等记录（唯一索引 = 最后一道防线）';
