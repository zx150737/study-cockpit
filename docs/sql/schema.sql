-- ============================================================================
-- 学习监控驾驶舱平台 (study-cockpit) · MySQL 8.0 完整建表语句
-- ----------------------------------------------------------------------------
-- 版本：V1 初始化脚本（第一步交付，可直接执行）
-- 目标版本：MySQL 8.0.16+（依赖 CHECK 约束生效）
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci
-- 时区约定：所有 DATETIME / DATE 均存储 UTC+8 (Asia/Shanghai) 墙上时间；
--           使用 DATETIME 而非 TIMESTAMP，避免会话时区隐式转换导致周次错乱。
-- 执行方式：mysql -u root -p < schema.sql
-- 安全提示：本脚本不含任何密钥；账号密码请通过 deploy/scripts/init-user.sh
--           用 BCrypt 生成密文后写入，禁止明文。
-- ============================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS study_cockpit
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE study_cockpit;

-- ----------------------------------------------------------------------------
-- 1. 系统用户表
--    password 存 BCrypt(strength=12) 密文；token_version 用于改密后令旧 JWT 全部失效
-- ----------------------------------------------------------------------------
CREATE TABLE sys_user (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(64)  NOT NULL                COMMENT '登录用户名',
  password      VARCHAR(100) NOT NULL                COMMENT 'BCrypt 加盐密文',
  nickname      VARCHAR(64)  DEFAULT NULL            COMMENT '昵称(展示用)',
  status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1正常 0禁用',
  token_version INT          NOT NULL DEFAULT 0      COMMENT '令牌版本, 改密后+1使旧JWT全部失效',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  CONSTRAINT ck_user_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

-- ----------------------------------------------------------------------------
-- 2. 备考目标表（考研 / 教师编 / 自定义）
--    逻辑删除，保证历史归档与周结算数据不被破坏
-- ----------------------------------------------------------------------------
CREATE TABLE study_goal (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT UNSIGNED NOT NULL                COMMENT '所属用户(数据隔离)',
  name       VARCHAR(128) NOT NULL                   COMMENT '目标名称, 如 2027考研/教师编/自定义',
  deleted    TINYINT      NOT NULL DEFAULT 0        COMMENT '0正常 1已删除(逻辑)',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_del (user_id, deleted),
  KEY idx_user_id  (user_id, id),
  CONSTRAINT ck_goal_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='备考目标表';

-- ----------------------------------------------------------------------------
-- 3. 科目表（目标下自由增删，支持默认权重）
--    default_weight 在创建任务时作为权重默认值带入，用户可覆盖
-- ----------------------------------------------------------------------------
CREATE TABLE study_subject (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id        BIGINT UNSIGNED NOT NULL                COMMENT '所属用户(冗余, 便于隔离过滤)',
  goal_id        BIGINT UNSIGNED NOT NULL                COMMENT '所属备考目标',
  name           VARCHAR(128) NOT NULL                   COMMENT '科目名称',
  default_weight DECIMAL(5,2) NOT NULL DEFAULT 1.00     COMMENT '默认权重',
  deleted        TINYINT      NOT NULL DEFAULT 0        COMMENT '0正常 1已删除(逻辑)',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_goal_del (user_id, goal_id, deleted),
  KEY idx_goal_id       (goal_id, id),
  CONSTRAINT ck_subject_weight  CHECK (default_weight > 0),
  CONSTRAINT ck_subject_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科目表';

-- ----------------------------------------------------------------------------
-- 4. 每日任务表
--    task_date = 归属日(日/周聚合) ; deadline = 截止时刻(积压判定)
--    goal_id 冗余存储, 使按目标筛选/导出/归档无需 JOIN 科目表
-- ----------------------------------------------------------------------------
CREATE TABLE daily_task (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT UNSIGNED NOT NULL                COMMENT '所属用户(数据隔离)',
  goal_id       BIGINT UNSIGNED NOT NULL                COMMENT '冗余目标ID, 便于按目标聚合/导出',
  subject_id    BIGINT UNSIGNED NOT NULL                COMMENT '所属科目',
  title         VARCHAR(255) NOT NULL                   COMMENT '任务内容',
  weight        DECIMAL(5,2) NOT NULL DEFAULT 1.00      COMMENT '任务权重(默认取科目默认权重)',
  task_date     DATE         NOT NULL                   COMMENT '任务归属日(UTC+8), 用于日/周聚合',
  deadline      DATETIME     DEFAULT NULL               COMMENT '截止时间(UTC+8)',
  mastery       TINYINT      DEFAULT NULL               COMMENT '掌握度 1-5',
  review_note   VARCHAR(1000) DEFAULT NULL              COMMENT '复盘备注',
  finish_status TINYINT      NOT NULL DEFAULT 0         COMMENT '0未完成 1已完成',
  finished_at   DATETIME     DEFAULT NULL               COMMENT '完成时间(UTC+8)',
  deleted       TINYINT      NOT NULL DEFAULT 0         COMMENT '0正常 1已删除(逻辑)',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_date_del      (user_id, task_date, deleted),                          -- 当日/本周趋势聚合
  KEY idx_user_goal_date_del (user_id, goal_id, task_date, deleted),                -- 按目标周导出/归档
  KEY idx_user_subject_date  (user_id, subject_id, task_date, deleted),             -- 科目完成度
  KEY idx_backlog            (user_id, finish_status, deleted, task_date, deadline),-- 积压/逾期扫描
  KEY idx_subject_id         (subject_id),
  CONSTRAINT ck_task_mastery       CHECK (mastery BETWEEN 1 AND 5),
  CONSTRAINT ck_task_weight        CHECK (weight > 0),
  CONSTRAINT ck_task_finish_status CHECK (finish_status IN (0, 1)),
  CONSTRAINT ck_task_deleted       CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日任务表';

-- ----------------------------------------------------------------------------
-- 5. 任务图片存储记录表（新增 · 15 天有效）
--    relative_path 仅由后端生成(年/月/UUID.ext), 绝不采用用户原始文件名
--    expire_time = upload_time + INTERVAL 15 DAY, 由 ImageExpireCleanTask 扫描清理
--    到期时物理删除本表记录 + 磁盘文件（对应需求「清除数据库内对应的图片记录」）
-- ----------------------------------------------------------------------------
CREATE TABLE task_image (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id        BIGINT UNSIGNED NOT NULL                COMMENT '归属用户(图片访问鉴权)',
  task_id        BIGINT UNSIGNED NOT NULL                COMMENT '绑定任务',
  original_name  VARCHAR(255) NOT NULL                   COMMENT '原始文件名, 仅展示, 绝不用于落盘路径',
  stored_name    VARCHAR(128) NOT NULL                   COMMENT 'UUID 重命名后的文件名',
  relative_path  VARCHAR(512) NOT NULL                   COMMENT '相对存储根路径, 如 2026/11/<uuid>.jpg',
  ext            VARCHAR(10)  NOT NULL                   COMMENT '规范化后缀: jpg/png/webp',
  mime_type      VARCHAR(64)  NOT NULL                   COMMENT '文件头探测出的真实 MIME',
  file_size      BIGINT UNSIGNED NOT NULL                COMMENT '文件字节数',
  magic_verified TINYINT      NOT NULL DEFAULT 1        COMMENT '文件头魔数校验通过标记',
  upload_time    DATETIME     NOT NULL                   COMMENT '上传时间(UTC+8), 15 天计时起点',
  expire_time    DATETIME     NOT NULL                   COMMENT '过期时间 = upload_time + INTERVAL 15 DAY',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_task (user_id, task_id),   -- 列表查询 + 归属校验
  KEY idx_expire    (expire_time),        -- 过期清理任务扫描(核心索引)
  KEY idx_task_id   (task_id),            -- CSV 导出统计图片数量
  KEY idx_user_id   (user_id),
  CONSTRAINT ck_image_size   CHECK (file_size > 0),
  CONSTRAINT ck_image_expire CHECK (expire_time > upload_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务图片存储记录表(15天有效)';

-- ----------------------------------------------------------------------------
-- 6. 周结算快照表（支撑 周日结算 / 历史数据归档页 / 学习基线对比）
--    goal_id = 0 为「全部目标汇总」哨兵值, 避免 NULL 破坏唯一键幂等性
--    WeeklySettlementTask 用 INSERT ... ON DUPLICATE KEY UPDATE 保证重跑幂等
-- ----------------------------------------------------------------------------
CREATE TABLE weekly_summary (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id         BIGINT UNSIGNED NOT NULL                COMMENT '所属用户',
  goal_id         BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '目标ID; 0=全部目标汇总',
  week_start      DATE         NOT NULL                   COMMENT '周一(UTC+8)',
  week_end        DATE         NOT NULL                   COMMENT '周日(UTC+8)',
  task_total      INT          NOT NULL DEFAULT 0        COMMENT '任务总数',
  task_finished   INT          NOT NULL DEFAULT 0        COMMENT '已完成任务数',
  weight_total    DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '总权重',
  weight_finished DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '已完成权重',
  completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00     COMMENT '加权完成率(%)',
  avg_daily_rate  DECIMAL(5,2) NOT NULL DEFAULT 0.00     COMMENT '本周平均每日完成率(%)',
  checkin_days    INT          NOT NULL DEFAULT 0        COMMENT '本周打卡天数',
  snapshot_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_goal_week (user_id, goal_id, week_start),  -- 结算幂等
  KEY idx_user_week (user_id, week_start),                       -- 历史归档列表/基线对比
  CONSTRAINT ck_week_rate CHECK (completion_rate BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='周结算快照表(支撑周日结算/归档/基线)';

-- ============================================================================
-- 初始化账号（部署阶段执行，不在本脚本内硬编码任何密码）
-- ----------------------------------------------------------------------------
-- 方式 A：deploy/scripts/init-user.sh 生成 BCrypt 密文后执行：
--   INSERT INTO sys_user (username, password, nickname)
--   VALUES ('<your_username>', '<bcrypt_hash_from_script>', '<nickname>');
--
-- 方式 B：首次部署临时设置 APP_ALLOW_REGISTER=true，
--         调用 POST /api/auth/register 创建账号后立即改回 false 并重启。
--
-- 生成随机密钥示例（仅示意，禁止写入仓库）：
--   openssl rand -base64 48   # JWT_SECRET / FILE_TOKEN_SECRET 需分别生成
--
-- MySQL 时区建议（my.cnf）：
--   [mysqld]
--   default-time-zone = '+08:00'
-- ============================================================================
