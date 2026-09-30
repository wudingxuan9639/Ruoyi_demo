-- =============================================================
-- RuoYi Demo —— TiDB 初始化脚本
-- 用法（两种方式任选其一）：
--   1) 手工导入：mysql -h <tidb-host> -P 4000 -u root < sql/tidb_account.sql
--   2) 自动建表：先执行本脚本建库，应用启动时再由 classpath:sql/schema.sql 建表
-- =============================================================

CREATE DATABASE IF NOT EXISTS ruoyi_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE ruoyi_demo;

CREATE TABLE IF NOT EXISTS demo_account (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '账户ID',
    name       VARCHAR(64)  NOT NULL                COMMENT '账户名称',
    balance    DECIMAL(18,2) NOT NULL DEFAULT 0     COMMENT '余额',
    remark     VARCHAR(255)      NULL               COMMENT '备注',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='账户表';

-- 示例数据
INSERT IGNORE INTO demo_account (id, name, balance, remark) VALUES
    (1, 'alice',   1000.00, '示例账户'),
    (2, 'bob',     1000.00, '示例账户'),
    (3, 'carol',    500.00, '示例账户');

-- 生产环境建议创建专用账号，不要直接使用 root
-- CREATE USER IF NOT EXISTS 'ruoyi'@'%' IDENTIFIED BY '请改成强密码';
-- GRANT SELECT,INSERT,UPDATE,DELETE ON ruoyi_demo.* TO 'ruoyi'@'%';

-- 验证
SELECT COUNT(*) AS cnt, SUM(balance) AS sum_balance FROM demo_account;
