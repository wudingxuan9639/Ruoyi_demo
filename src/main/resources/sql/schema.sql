-- Demo 便利脚本：应用启动时幂等建表（库需先存在）
-- 生产环境建议关闭 spring.sql.init.mode，改由 DBA 执行 sql/tidb_account.sql
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
