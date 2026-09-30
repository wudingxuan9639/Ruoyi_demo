package com.ruoyi.project.demo.domain;

import java.math.BigDecimal;
import java.util.Date;

import com.ruoyi.framework.web.domain.BaseEntity;

/**
 * 账户对象 demo_account（对应 RuoYi 的 domain 层）。
 */
public class Account extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 账户ID */
    private Long id;

    /** 账户名称 */
    private String name;

    /** 余额 */
    private BigDecimal balance;

    /** 创建时间（演示用：允许手工指定，默认数据库填充） */
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
