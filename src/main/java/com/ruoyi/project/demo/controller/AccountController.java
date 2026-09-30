package com.ruoyi.project.demo.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.framework.web.controller.BaseController;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.framework.web.domain.TableDataInfo;
import com.ruoyi.project.demo.domain.Account;
import com.ruoyi.project.demo.service.IAccountService;

/**
 * 账户信息操作处理（对应 RuoYi 的 controller 层）。
 */
@RestController
@RequestMapping("/demo/account")
public class AccountController extends BaseController {

    @Autowired
    private IAccountService accountService;

    /** 查询账户列表（分页 + 关键字搜索） */
    @GetMapping("/list")
    public TableDataInfo list(Account account,
                              @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                              @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        startPage(pageNum, pageSize);
        List<Account> list = accountService.selectAccountList(account);
        return getDataTable(list);
    }

    /** 根据ID获取详细信息 */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        Account account = accountService.selectAccountById(id);
        if (account == null) {
            return AjaxResult.error("账户不存在，ID=" + id);
        }
        return AjaxResult.success(account);
    }

    /** 新增账户 */
    @PostMapping
    public AjaxResult add(@RequestBody Account account) {
        if (account.getBalance() == null) {
            account.setBalance(BigDecimal.ZERO);
        }
        return toAjax(accountService.insertAccount(account));
    }

    /** 修改账户 */
    @PutMapping
    public AjaxResult edit(@RequestBody Account account) {
        return toAjax(accountService.updateAccount(account));
    }

    /** 删除账户（支持逗号分隔批量删除，对齐 RuoYi 习惯） */
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable("ids") String ids) {
        return toAjax(accountService.deleteAccountByIds(ids));
    }

    /** 转账：事务演示 */
    @PostMapping("/transfer")
    public AjaxResult transfer(@RequestBody Map<String, Object> body) {
        try {
            Long fromId = Long.valueOf(String.valueOf(body.get("fromId")));
            Long toId = Long.valueOf(String.valueOf(body.get("toId")));
            BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
            int rows = accountService.transfer(fromId, toId, amount);
            return AjaxResult.success("转账成功，影响行数 " + rows);
        } catch (NumberFormatException e) {
            return AjaxResult.error("参数格式错误：" + e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 聚合统计 */
    @GetMapping("/stats")
    public AjaxResult stats() {
        Map<String, Object> data = new HashMap<>(accountService.selectAccountStats());
        data.put("dbVersion", accountService.selectDbVersion());
        return AjaxResult.success(data);
    }
}
