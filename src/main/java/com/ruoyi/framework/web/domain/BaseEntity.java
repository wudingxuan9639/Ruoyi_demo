package com.ruoyi.framework.web.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * Entity 基类（RuoYi BaseEntity 简化版）。
 */
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 搜索值（用于模糊查询） */
    private String searchValue;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 备注 */
    private String remark;

    public String getSearchValue() { return searchValue; }
    public void setSearchValue(String searchValue) { this.searchValue = searchValue; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
