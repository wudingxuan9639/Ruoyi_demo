package com.ruoyi.framework.web.controller;

import java.util.List;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.framework.web.domain.AjaxResult;
import com.ruoyi.framework.web.domain.TableDataInfo;

/**
 * web 层通用数据处理（RuoYi BaseController 简化版）。
 */
public class BaseController {

    /**
     * 设置请求分页数据（PageHelper 会在下一条查询上自动追加 limit）。
     */
    protected void startPage(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
    }

    /**
     * 封装分页对象。
     */
    protected TableDataInfo getDataTable(List<?> list) {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(200);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo<>(list).getTotal());
        return rspData;
    }

    /**
     * 响应返回结果（影响行数转 AjaxResult）。
     */
    protected AjaxResult toAjax(int rows) {
        return rows > 0 ? AjaxResult.success() : AjaxResult.error();
    }

    protected AjaxResult toAjax(boolean result) {
        return result ? AjaxResult.success() : AjaxResult.error();
    }
}
