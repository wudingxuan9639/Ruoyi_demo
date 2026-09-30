package com.ruoyi.project.demo.service;

import java.util.List;
import java.util.Map;

import com.ruoyi.project.demo.domain.Account;

/**
 * 账户 服务层（对应 RuoYi 的 service 接口）。
 */
public interface IAccountService {

    /** 分页查询账户列表 */
    List<Account> selectAccountList(Account account);

    /** 根据ID查询 */
    Account selectAccountById(Long id);

    /** 新增 */
    int insertAccount(Account account);

    /** 修改 */
    int updateAccount(Account account);

    /** 批量删除（ids 形如 "1,2,3"） */
    int deleteAccountByIds(String ids);

    /** 转账：在同一事务内扣款与加款，余额不足回滚 */
    int transfer(Long fromId, Long toId, java.math.BigDecimal amount);

    /** 聚合统计 */
    Map<String, Object> selectAccountStats();

    /** 数据库版本 */
    String selectDbVersion();
}
