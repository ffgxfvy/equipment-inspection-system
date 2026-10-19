-- ============================================================
-- 工业设备巡检管理系统 —— 建表脚本
-- 语法兼容 H2 (MODE=MySQL) 与 MySQL 8
--
-- 说明：
--   所有业务表都带 deleted 字段（逻辑删除）与 create_time / update_time，
--   这是 MyBatis-Plus 的约定：删除只置 deleted=1，避免误删导致数据无法追溯。
-- ============================================================

DROP TABLE IF EXISTS inspection_record;
DROP TABLE IF EXISTS inspection_task;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS sys_user;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 哈希）',
    real_name   VARCHAR(50)  NOT NULL COMMENT '姓名',
    role        VARCHAR(20)  NOT NULL DEFAULT 'INSPECTOR' COMMENT '角色：ADMIN 管理员 / INSPECTOR 巡检员',
    phone       VARCHAR(20)           COMMENT '手机号',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
    remark      VARCHAR(200)          COMMENT '备注',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    create_time DATETIME     NOT NULL COMMENT '创建时间',
    update_time DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) COMMENT '系统用户';

-- ------------------------------------------------------------
-- 设备台账
-- ------------------------------------------------------------
CREATE TABLE equipment (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code                VARCHAR(50)  NOT NULL COMMENT '设备编号',
    name                VARCHAR(100) NOT NULL COMMENT '设备名称',
    category            VARCHAR(50)  NOT NULL COMMENT '设备类目',
    model               VARCHAR(100)          COMMENT '规格型号',
    manufacturer        VARCHAR(100)          COMMENT '生产厂商',
    location            VARCHAR(200) NOT NULL COMMENT '安装位置',
    status              VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING 运行 / STOPPED 停机 / FAULT 故障 / MAINTENANCE 检修中',
    inspect_cycle_days  INT          NOT NULL DEFAULT 30 COMMENT '巡检周期（天）',
    last_inspect_time   DATETIME              COMMENT '上次巡检时间',
    next_inspect_time   DATETIME              COMMENT '下次应巡检时间',
    install_date        DATE                  COMMENT '投运日期',
    -- 预留的采集字段：可直接对接现场仪表/网关上报的数值
    last_temperature    DECIMAL(10, 2)        COMMENT '最近温度读数',
    last_pressure       DECIMAL(10, 2)        COMMENT '最近压力读数',
    remark              VARCHAR(500)          COMMENT '备注',
    deleted             TINYINT      NOT NULL DEFAULT 0,
    create_time         DATETIME     NOT NULL,
    update_time         DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_code (code),
    KEY idx_equipment_status (status),
    KEY idx_equipment_next_inspect (next_inspect_time)
) COMMENT '设备台账';

-- ------------------------------------------------------------
-- 巡检任务
-- ------------------------------------------------------------
CREATE TABLE inspection_task (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no      VARCHAR(50)  NOT NULL COMMENT '任务编号',
    name         VARCHAR(100) NOT NULL COMMENT '任务名称',
    equipment_id BIGINT       NOT NULL COMMENT '设备 ID',
    inspector_id BIGINT                COMMENT '巡检员 ID',
    plan_time    DATETIME     NOT NULL COMMENT '计划巡检时间',
    finish_time  DATETIME              COMMENT '实际完成时间',
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待执行 / DOING 执行中 / DONE 已完成 / CANCELLED 已取消',
    priority     VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：LOW / NORMAL / HIGH / URGENT',
    remark       VARCHAR(500)          COMMENT '备注',
    deleted      TINYINT      NOT NULL DEFAULT 0,
    create_time  DATETIME     NOT NULL,
    update_time  DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_task_status (status),
    KEY idx_task_equipment (equipment_id),
    KEY idx_task_inspector (inspector_id)
) COMMENT '巡检任务';

-- ------------------------------------------------------------
-- 巡检记录
-- ------------------------------------------------------------
CREATE TABLE inspection_record (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id        BIGINT                COMMENT '关联任务 ID（现场临时巡检可为空）',
    equipment_id   BIGINT       NOT NULL COMMENT '设备 ID',
    inspector_id   BIGINT       NOT NULL COMMENT '巡检员 ID',
    inspect_time   DATETIME     NOT NULL COMMENT '巡检时间',
    result         VARCHAR(20)  NOT NULL COMMENT '结论：NORMAL 正常 / ABNORMAL 异常',
    abnormal_desc  VARCHAR(500)          COMMENT '异常描述',
    handle_status  VARCHAR(20)  NOT NULL DEFAULT 'UNHANDLED' COMMENT '处理状态：UNHANDLED 待处理 / HANDLING 处理中 / HANDLED 已处理',
    handle_remark  VARCHAR(500)          COMMENT '处理说明',
    photo_url      VARCHAR(300)          COMMENT '现场照片',
    temperature    DECIMAL(10, 2)        COMMENT '实测温度',
    pressure       DECIMAL(10, 2)        COMMENT '实测压力',
    remark         VARCHAR(500)          COMMENT '备注',
    deleted        TINYINT      NOT NULL DEFAULT 0,
    create_time    DATETIME     NOT NULL,
    update_time    DATETIME,
    PRIMARY KEY (id),
    KEY idx_record_equipment (equipment_id),
    KEY idx_record_inspector (inspector_id),
    KEY idx_record_time (inspect_time),
    KEY idx_record_handle (handle_status)
) COMMENT '巡检记录';
