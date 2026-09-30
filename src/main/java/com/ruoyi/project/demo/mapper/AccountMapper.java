package com.ruoyi.project.demo.mapper;

import java.util.List;
import java.util.Map;

import com.ruoyi.project.demo.domain.Account;

/**
 * 账户 数据层（对应 RuoYi 的 mapper 接口，SQL 见 resources/mapper/demo/AccountMapper.xml）。
 */
public interface AccountMapper {

    /** 查询账户列表（带条件） */
    List<Account> selectAccountList(Account account);

    /** 根据ID查询 */
    Account selectAccountById(Long id);

    /** 新增 */
    int insertAccount(Account account);

    /** 修改 */
    int updateAccount(Account account);

    /** 删除（单个） */
    int deleteAccountById(Long id);

    /** 批量删除 */
    int deleteAccountByIds(Long[] ids);

    /** 扣款（带余额条件，返回影响行数；余额不足返回 0） */
    int deductBalance(Long id, java.math.BigDecimal amount);

    /** 加款 */
    int addBalance(Long id, java.math.BigDecimal amount);

    /** 聚合统计 */
    Map<String, Object> selectAccountStats();

    /** 数据库版本（用于页面展示当前连接的是 TiDB 还是 MySQL） */
    String selectDbVersion();
}
