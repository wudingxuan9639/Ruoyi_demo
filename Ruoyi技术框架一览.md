# Ruoyi 技术框架一览

> 适用对象：本仓库 `Ruoyi_demo`（**RuoYi 脚手架版**：Spring Boot 3 + MyBatis + Druid + Vue3 + Element Plus，对接 TiDB）
> 文档目的：①讲清本工程每一层用到的技术；②与 RuoYi（若依）官方完整技术栈做逐层对照；③给出补齐到完整 RuoYi 的清单。
> 编写日期：2026-09-30

---

## 0. 本工程与 RuoYi 的关系

本工程是 **RuoYi-Vue（Spring Boot 3.x 分支）的最小可用子集**：保留了 RuoYi 的分层结构、响应范式、
数据源与分页方案，去掉了权限、缓存、定时任务、代码生成等平台级模块，只留一个 `demo_account` 表的增删改查，
用于验证「应用 → TiDB」链路。

```
完整 RuoYi = 本工程 + 鉴权(Security/JWT) + Redis + Quartz + 代码生成 + 系统管理模块 + 独立前端工程
```

---

## 一、技术全景

```
┌────────────────────────────────────────────────────────────────┐
│ 前端  Vue 3.5 + Element Plus 2.9 + Axios 1.7                    │
│       单页 index.html，依赖本地化到 static/vendor，无需 Node 构建 │
└───────────────┬────────────────────────────────────────────────┘
                │ HTTP / JSON (Axios)
┌───────────────▼────────────────────────────────────────────────┐
│ Web 层    Spring MVC（内嵌 Tomcat 10）                          │
│           @RestController / @RequestMapping + Jackson 序列化     │
├────────────────────────────────────────────────────────────────┤
│ 业务层    Spring Service + @Transactional（声明式事务）          │
├────────────────────────────────────────────────────────────────┤
│ 数据层    MyBatis 3.5（Mapper 接口 + XML）                      │
│           PageHelper 2.1 分页 / Druid 1.2 连接池                 │
└───────────────┬────────────────────────────────────────────────┘
                │ MySQL 8.0 协议（mysql-connector-j 8.4.0）
┌───────────────▼────────────────────────────────────────────────┐
│ 存储层    TiDB v8.5.7  tidb-server(4000) / pd-server(2379)      │
│                        / tikv-server(20160)                     │
└────────────────────────────────────────────────────────────────┘

构建：Maven + spring-boot-maven-plugin → fat jar
运行：JDK 17 + Spring Boot 3.3.13（Spring Framework 6.1）
```

---

## 二、后端技术明细（本工程实际使用）

| 技术 | 版本 | 作用 | 位置 |
|---|---|---|---|
| **Spring Boot** | 3.3.13 | 自动配置、内嵌容器、起步依赖管理 | `pom.xml`（parent） |
| **Spring Framework** | 6.1.x | IoC / AOP / 声明式事务 | 由 Boot 传递 |
| **Spring MVC** | 6.1.x | `@RestController`、`@GetMapping/@PostMapping` 等 | `AccountController` |
| **内嵌 Tomcat** | 10.1.x | HTTP 服务，默认端口 8080 | 自动装配 |
| **Jackson** | 2.17.x | JSON 序列化 / 反序列化，时区 GMT+8 | Boot 自动装配 |
| **Spring Validation** | 3.3 | 参数校验（`spring-boot-starter-validation`） | 已引入 |
| **MyBatis** | 3.5.14 | ORM，SQL 与代码分离 | `mapper/demo/AccountMapper.xml` |
| **mybatis-spring-boot-starter** | 3.0.3 | MyBatis 与 Spring 集成、Mapper 扫描 | `MyBatisConfig` |
| **Druid** | 1.2.23 | 数据源连接池 + 监控统计 + 慢 SQL | `application-druid.yml` |
| **PageHelper** | 2.1.1 | 物理分页（自动追加 `limit`） | `BaseController.startPage()` |
| **mysql-connector-j** | 8.4.0 | JDBC 驱动（TiDB 走 MySQL 协议） | 数据源 |
| **Logback** | 1.5.x | 日志 | Boot 默认 |
| **Maven** | 3.9.16 | 构建打包 | `pom.xml` |

### 2.1 RuoYi 范式在代码中的体现

```java
// Controller：继承 BaseController，沿用 RuoYi 的分页与响应写法
@GetMapping("/list")
public TableDataInfo list(Account account,
                          @RequestParam(defaultValue = "1") int pageNum,
                          @RequestParam(defaultValue = "10") int pageSize) {
    startPage(pageNum, pageSize);                       // PageHelper.startPage
    List<Account> list = accountService.selectAccountList(account);
    return getDataTable(list);                          // 封装 total + rows
}

// Service：声明式事务（RuoYi 同样用 @Transactional）
@Transactional(rollbackFor = Exception.class)
public int transfer(Long fromId, Long toId, BigDecimal amount) {
    int deducted = accountMapper.deductBalance(fromId, amount);  // where balance >= amount
    if (deducted == 0) throw new IllegalStateException("付款方余额不足，已回滚");
    return deducted + accountMapper.addBalance(toId, amount);
}
```

- **响应体**：`AjaxResult{code,msg,data}`、`TableDataInfo{total,rows,code,msg}`
- **实体**：`Account extends BaseEntity`（`searchValue / createTime / updateTime / remark`）
- **数据层**：Mapper 接口 + XML（`<sql>` 片段、`useGeneratedKeys` 回填主键、`<foreach>` 批量删除）
- **配置分离**：`application.yml`（通用）+ `application-druid.yml`（数据源）

### 2.2 PageHelper 关键配置

```yaml
pagehelper:
  helperDialect: mysql      # TiDB 用 mysql 方言
  reasonable: true          # 页码越界自动修正
  supportMethodsArguments: true
  params: count=countSql
```

---

## 三、前端技术明细

| 技术 | 版本 | 用法 |
|---|---|---|
| **Vue 3** | 3.5.13 | `createApp` + 组合式 API（`setup`、`ref`、`reactive`、`onMounted`） |
| **Element Plus** | 2.9.7 | `el-table`（列表/分页）、`el-dialog`（新增/编辑/转账）、`el-form`、`el-pagination`、`ElMessage`、`ElMessageBox` |
| **Axios** | 1.7.9 | `GET/POST/PUT/DELETE` 与后端 REST 交互 |
| **原生 CSS3** | — | CSS 变量主题、Grid 统计卡片、Flex 工具栏、响应式 |

依赖文件已下载到 `src/main/resources/static/vendor/`（vue / element-plus js+css / axios），
由 Spring Boot 静态资源直接托管：**服务器无外网也能正常打开页面，且不需要 Node/npm 构建**。

页面能力：统计卡片、分页 + 关键字搜索、新增/编辑对话框、删除二次确认、转账（事务）、数据库版本展示。

> 完整 RuoYi 前端是独立工程（Vue CLI 或 Vite 构建、Vue Router、Pinia、权限指令等），
> 本工程把"页面"压缩成单文件，逻辑等价、便于直接运行。

---

## 四、数据层：TiDB

| 项 | 值 |
|---|---|
| 版本 | TiDB **v8.5.7**（`SELECT VERSION()` → `8.0.11-TiDB-v8.5.7`） |
| 组件 | tidb-server 4000（MySQL 协议）、pd-server 2379（调度/TSO）、tikv-server 20160（存储） |
| 兼容性 | 兼容 MySQL 8.0 协议与语法，现有 JDBC/Druid/MyBatis 基本可直接用 |
| 事务 | 分布式事务（Percolator 两阶段提交）；`@Transactional` 的提交/回滚由 TiDB 保证 |
| 分页/统计 | `LIMIT/OFFSET`、`COUNT/SUM/MAX/MIN` 正常下推到 TiKV |

**迁移时注意的 TiDB ↔ MySQL 差异**（以官方文档为准）：

- 不支持存储过程、触发器、自定义函数、物化视图；普通视图只读
- 外键约束支持有限，引用完整性建议放应用层
- `AUTO_INCREMENT` 只保证唯一与递增趋势，**不保证连续**
- 单事务有数据量上限（默认约 100MB 量级），批量写入要分批提交
- DDL 为在线变更，大表变更需评估；部分系统表/变量与 MySQL 不同
- 使用 Druid 时不要开启 `wall` 防火墙过滤器，避免误拦截

---

## 五、RuoYi（若依）官方完整技术栈

依据官方站点与文档整理（版本分支随官方更新，以 <https://doc.ruoyi.vip> 为准）。

### 5.1 后端

| 分类 | 技术 | 作用 |
|---|---|---|
| 核心 | Spring Boot 2.x / 3.x / 4.x（分支并行维护） | 自动配置、内嵌容器 |
| IoC/AOP/事务 | Spring Framework | 依赖注入、切面、声明式事务 |
| 安全 | **Spring Security + JWT** | 登录认证、RBAC 权限、无状态令牌 |
| 持久层 | MyBatis（社区增强版常用 MyBatis-Plus） | ORM |
| 连接池 | Alibaba Druid | 数据源、监控、慢 SQL |
| 数据库 | MySQL（社区版支持 Oracle/PG/达梦/TiDB 等） | 存储 |
| 缓存 | Redis（+ Redisson） | 会话、字典、权限、限流 |
| 分页 | PageHelper | 物理分页 |
| 定时任务 | Quartz | 在线调度、支持集群 |
| 接口文档 | Knife4j / Swagger | API 文档与调试 |
| 工具 | Hutool、Lombok、fastjson2、POI、Velocity | 通用工具 |
| 校验 | Hibernate Validator | 参数校验 |
| 构建 | Maven（多模块） | 依赖与模块管理 |

### 5.2 前端

| | **RuoYi-Vue2** | **RuoYi-Vue3**（官方主推） |
|---|---|---|
| 框架 | Vue 2 | Vue 3 |
| 构建 | Vue CLI（Webpack） | **Vite** |
| UI 库 | Element UI | **Element Plus** |
| 状态管理 | Vuex 3 | **Pinia** |
| 路由 | Vue Router 3 | Vue Router 4 |
| 请求 | Axios | Axios |

前后端可混搭（如后端 Boot 2.x + 前端 Vue3）。

### 5.3 家族版本

| 版本 | 技术要点 | 适用 |
|---|---|---|
| RuoYi（不分离） | Spring Boot + Shiro + Thymeleaf + Bootstrap | 单体、服务端渲染 |
| **RuoYi-Vue** | Spring Boot + Security + JWT + MyBatis + Vue | **最常用**，前后端分离 |
| RuoYi-Cloud | Spring Cloud & Alibaba（Nacos/Gateway/Sentinel） | 微服务 |
| RuoYi-App | UniApp + Vue | 移动端 |
| RuoYi-Vue-Plus / ruoyi-vue-pro | MyBatis-Plus、多租户、Flowable 工作流等 | 需要更多开箱能力 |

---

## 六、本工程 ↔ 完整 RuoYi 对照表

| 层 | 本工程 | 完整 RuoYi | 差距 |
|---|---|---|---|
| Web | `@RestController` + Tomcat | 同 + 全局异常处理、日志切面、权限注解 | 补 `GlobalExceptionHandler`、操作日志 |
| 响应体 | `AjaxResult` / `TableDataInfo` | 同 | ✅ 一致 |
| 业务 | `@Service` + `@Transactional` | 同 + 数据权限切面 | 单表演示无需数据权限 |
| 数据访问 | MyBatis + XML + PageHelper | 同（或 MyBatis-Plus） | ✅ 一致 |
| 数据源 | Druid | 同（多数据源可选） | ✅ 一致 |
| 配置 | `application.yml` + `application-druid.yml` | 同 + Nacos（Cloud 版） | 单机无需配置中心 |
| 鉴权 | **无** | Spring Security + JWT + RBAC | **必须补** |
| 缓存 | **无** | Redis（字典/权限/会话） | 高并发场景补 |
| 定时任务 | **无** | Quartz | 按需 |
| 代码生成 | **无** | Velocity 模板生成前后端代码 | 按需 |
| 前端 | 单页 + 本地 vendor | 独立工程（Vite/Router/Pinia/权限指令） | 逻辑可平移，工程化需重建 |
| 模块划分 | 单模块 | admin/framework/common/system/quartz 多模块 | 规模变大后再拆 |

---

## 七、补齐到完整 RuoYi 的清单

1. **安全**：引入 `spring-boot-starter-security` + JWT，登录、菜单权限、按钮级权限、数据权限（对照 RuoYi 的 `framework/web/service` 与 `SysPermissionService`）
2. **缓存**：Redis + Redisson，缓存字典、权限、登录态
3. **全局异常**：`@RestControllerAdvice`（RuoYi 的 `GlobalExceptionHandler`）
4. **日志**：操作日志/登录日志切面（RuoYi 的 `LogAspect` + `@Log` 注解）
5. **多模块**：按 `ruoyi-admin / ruoyi-framework / ruoyi-common / ruoyi-system` 拆分
6. **前端工程化**：把 `static/index.html` 迁到 Vite + Vue3 工程（Vue Router + Pinia + 权限指令 + 面包屑）
7. **系统模块**：用户/部门/岗位/角色/菜单/字典/参数/通知公告
8. **定时任务**：Quartz + 在线任务管理
9. **代码生成**：Velocity 模板一键生成 Controller/Service/Mapper/Vue/SQL
10. **监控**：Spring Boot Actuator + Druid 监控页 + 服务监控

---

## 八、本次验证环境

| 项 | 值 |
|---|---|
| 构建运行机 | macOS + JDK 17（Zulu 17.0.20）+ Maven 3.9.16 |
| 数据库 | 服务器 TiDB v8.5.7（113.249.106.160 内网 10.0.0.17，组件绑定 127.0.0.1） |
| 连接方式 | 本机 SSH 隧道 `ssh -L 4000:127.0.0.1:4000` → JDBC 连 127.0.0.1:4000 |
| 测试端口 | 18081（本机环境变量 `SERVER__PORT` 会覆盖 `server.port`，验证时显式指定） |
| 服务器 | Kylin V10 / 16 核 / 62 GiB，无外网（依赖需在能联网的机器构建后上传） |

---

## 九、参考链接

- RuoYi 官网：<https://ruoyi.vip>
- RuoYi 文档：<https://doc.ruoyi.vip/ruoyi-vue>
- RuoYi-Vue 源码：<https://gitee.com/y_project/RuoYi-Vue>
- MyBatis：<https://mybatis.org/mybatis-3/zh/index.html>
- PageHelper：<https://pagehelper.github.io>
- Druid：<https://github.com/alibaba/druid>
- TiDB 文档：<https://docs.pingcap.com/tidb/stable/overview>
- TiDB 与 MySQL 兼容性：<https://docs.pingcap.com/tidb/stable/mysql-compatibility>
