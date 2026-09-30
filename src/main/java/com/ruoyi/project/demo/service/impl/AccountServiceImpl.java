package com.ruoyi.project.demo.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ruoyi.project.demo.domain.Account;
import com.ruoyi.project.demo.mapper.AccountMapper;
import com.ruoyi.project.demo.service.IAccountService;

/**
 * 账户 服务层实现（对应 RuoYi 的 service.impl）。
 */
@Service
public class AccountServiceImpl implements IAccountService {

    @Autowired
    private AccountMapper accountMapper;

    @Override
    public List<Account> selectAccountList(Account account) {
        return accountMapper.selectAccountList(account);
    }

    @Override
    public Account selectAccountById(Long id) {
        return accountMapper.selectAccountById(id);
    }

    @Override
    public int insertAccount(Account account) {
        return accountMapper.insertAccount(account);
    }

    @Override
    public int updateAccount(Account account) {
        return accountMapper.updateAccount(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAccountByIds(String ids) {
        if (!StringUtils.hasText(ids)) {
            return 0;
        }
        String[] arr = ids.split(",");
        Long[] idArr = new Long[arr.length];
        for (int i = 0; i < arr.length; i++) {
            idArr[i] = Long.valueOf(arr[i].trim());
        }
        return accountMapper.deleteAccountByIds(idArr);
    }

    /**
     * 转账：先按条件扣款（balance >= amount），影响行数为 0 说明余额不足，抛异常触发回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int transfer(Long fromId, Long toId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("转账金额必须大于 0");
        }
        int deducted = accountMapper.deductBalance(fromId, amount);
        if (deducted == 0) {
            throw new IllegalStateException("付款方余额不足或账户不存在，已回滚");
        }
        int added = accountMapper.addBalance(toId, amount);
        if (added == 0) {
            throw new IllegalStateException("收款方账户不存在，已回滚");
        }
        return deducted + added;
    }

    @Override
    public Map<String, Object> selectAccountStats() {
        return accountMapper.selectAccountStats();
    }

    @Override
    public String selectDbVersion() {
        return accountMapper.selectDbVersion();
    }
}
