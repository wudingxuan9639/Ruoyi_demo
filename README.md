# Ruoyi_demo

基于 **RuoYi（若依）脚手架 Spring Boot 3.x 分支** 重写的增删改查示例：
后端 Spring Boot + MyBatis + Druid + PageHelper，前端 **Vue 3 + Element Plus + Axios**，
数据库对接 **TiDB v8.5.7**（MySQL 协议兼容）。

> 本仓库此前是"零第三方框架"版本（JDK 自带 HttpServer + JDBC + 原生 JS）。
> 现按你的要求用 RuoYi 脚手架重写并替换，旧版本保留在 `archive/no-framework-demo` 分支。
> 完整技术栈说明与 RuoYi 官方对照见 **[Ruoyi技术框架一览.md](./Ruoyi技术框架一览.md)**。

---

## 一、已验证结果（真实 TiDB，非模拟）

在服务器 TiDB v8.5.7 上实测通过（通过 SSH 隧道连接，本地启动应用）：

| # | 场景 | 结果 |
|---|---|---|
| 1 | 分页列表 `pageNum/pageSize` | 返回 `total=3`、`rows` 三条，`PageHelper` 自动追加 limit |
| 2 | 新增账户 dave | `{"code":200,"msg":"操作成功"}` |
| 3 | 修改余额 800 → 1500 | 成功，落库确认 |
| 4 | 按 ID 查询 | 返回完整实体 |
| 5 | 关键字搜索 `searchValue=dave` | 命中 1 条 |
| 6 | 聚合统计 | `cnt=4 sum=4000 max=1500 min=500`，`dbVersion=8.0.11-TiDB-v8.5.7` |
| 7 | 转账 100（1→2） | 事务提交，余额 1000/1000 → **900/1100** |
| 8 | 超额转账 999999 | 抛出业务异常 → **事务回滚**，余额不变 |
| 9 | 删除账户 | 成功，再查返回"账户不存在" |
| 10 | 页面与静态资源 | `index.html` / `vendor/*` 全部 200 |

---

## 二、目录结构（对齐 RuoYi 分层）

```
Ruoyi_demo/
├── pom.xml                                        # Spring Boot 3.3.13 父工程 + 依赖
├── sql/tidb_account.sql                           # 建库建表示例数据（TiDB）
├── src/main/java/com/ruoyi/
│   ├── RuoYiApplication.java                      # 启动类
│   ├── framework/config/MyBatisConfig.java        # @MapperScan
│   ├── framework/web/controller/BaseController.java  # startPage / getDataTable / toAjax
│   ├── framework/web/domain/{AjaxResult,TableDataInfo,BaseEntity}.java
│   └── project/demo/
│       ├── controller/AccountController.java      # /demo/account/**
│       ├── service/IAccountService.java
│       ├── service/impl/AccountServiceImpl.java   # @Transactional 转账与批量删除
│       ├── mapper/AccountMapper.java              # 数据层接口
│       └── domain/Account.java                    # 实体（继承 BaseEntity）
├── src/main/resources/
│   ├── application.yml                            # 主配置（端口、MyBatis、PageHelper）
│   ├── application-druid.yml                      # Druid 数据源（TiDB 4000 端口）
│   ├── mybatis/mybatis-config.xml
│   ├── mapper/demo/AccountMapper.xml              # SQL（含带余额条件的扣款）
│   ├── sql/schema.sql                             # 启动时幂等建表
│   └── static/
│       ├── index.html                             # Vue3 + Element Plus 管理页面
│       └── vendor/                                # 本地化的 vue/element-plus/axios（离线可用）
├── Ruoyi技术框架一览.md
└── README.md
```

---

## 三、快速开始

### 3.1 前置条件

- JDK 17+、Maven 3.6+
- TiDB（默认 4000）或 MySQL 5.7+
- 浏览器（前端依赖已本地化，**无需 Node/npm 构建**）

### 3.2 准备数据库

```bash
# 方式一：用 mysql 客户端导入
mysql -h <tidb-host> -P 4000 -u root < sql/tidb_account.sql

# 方式二：只建库，表由应用启动时自动创建（classpath:sql/schema.sql，幂等）
mysql -h <tidb-host> -P 4000 -u root -e "CREATE DATABASE IF NOT EXISTS ruoyi_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"
```

### 3.3 配置数据源

`src/main/resources/application-druid.yml`：

```yaml
spring:
  datasource:
    druid:
      url: jdbc:mysql://127.0.0.1:4000/ruoyi_demo?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
      username: ${DB_USERNAME:root}
      password: ${DB_PASSWORD:}
```

密码建议用环境变量注入（避免明文入库）：

```bash
export DB_USERNAME=ruoyi
export DB_PASSWORD='你的强密码'
```

端口在 `application.yml` 的 `server.port`（默认 8080）。

### 3.4 构建与运行

```bash
mvn -q -DskipTests package          # 产物 target/ruoyi-demo.jar
java -jar target/ruoyi-demo.jar     # 或 --server.port=18081 覆盖端口
```

浏览器打开 <http://127.0.0.1:8080/>（页面在 `static/index.html`，由 Spring Boot 直接托管）。

若数据库在远端服务器，先建隧道再连：

```bash
ssh -f -N -L 4000:127.0.0.1:4000 <user@host>     # 把远端 4000 映射到本机
```

---

## 四、接口清单（RuoYi 风格响应体）

统一响应：`{"code":200,"msg":"...","data":...}`；分页：`{"total":N,"rows":[...],"code":200}`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/demo/account/list?pageNum=1&pageSize=10&searchValue=` | 分页列表 + 关键字搜索 |
| GET | `/demo/account/{id}` | 单条详情 |
| POST | `/demo/account` | 新增 `{name,balance,remark}` |
| PUT | `/demo/account` | 修改 `{id,name,balance,remark}` |
| DELETE | `/demo/account/{ids}` | 删除，支持逗号分隔批量（如 `1,2,3`） |
| POST | `/demo/account/transfer` | 转账 `{fromId,toId,amount}`，余额不足回滚 |
| GET | `/demo/account/stats` | 聚合统计 + 数据库版本 |

```bash
curl 'http://127.0.0.1:8080/demo/account/list?pageNum=1&pageSize=5'
curl -X POST http://127.0.0.1:8080/demo/account -H 'Content-Type: application/json' -d '{"name":"dave","balance":800}'
curl -X POST http://127.0.0.1:8080/demo/account/transfer -H 'Content-Type: application/json' -d '{"fromId":1,"toId":2,"amount":100}'
```

---

## 五、与完整 RuoYi 的差异（本 Demo 精简掉了什么）

| 完整 RuoYi | 本 Demo | 说明 |
|---|---|---|
| Spring Security + JWT 登录鉴权 | **无** | 内网/本机验证用；生产必须补（见技术文档第 8 节） |
| Redis 缓存字典/权限 | **无** | 单表演示不需要 |
| Quartz 定时任务、代码生成、操作日志切面 | **无** | 保留分层与命名规范，便于对照学习 |
| 多模块（admin/framework/common/system/quartz） | **单模块** | 包路径仍按 `com.ruoyi.framework` / `com.ruoyi.project` 组织 |
| 前端独立工程（Vue CLI/Vite 构建） | **单页 + 本地 vendor** | 免 Node 构建，双击 jar 即用；可平滑迁到 Vite 工程 |
| MyBatis-Plus（社区版） | 原生 MyBatis + XML | 更贴近官方 RuoYi-Vue |

**保留的 RuoYi 核心**：分层结构（controller → service → mapper → domain）、`AjaxResult`/`TableDataInfo`/`BaseEntity`/`BaseController`、
`startPage()/getDataTable()/toAjax()` 分页与响应范式、Druid 数据源、PageHelper 分页、Mapper XML 写法、`application.yml + application-druid.yml` 配置分离。

---

## 六、技术栈速览

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5 + Element Plus 2.9 + Axios 1.7（本地化到 `static/vendor`，离线可用） |
| Web | Spring MVC（内嵌 Tomcat 10） |
| 业务 | Spring Service + `@Transactional` |
| 数据访问 | MyBatis 3.5 + PageHelper 2.1 + Druid 1.2 连接池 |
| 驱动 | mysql-connector-j 8.4.0 |
| 存储 | TiDB v8.5.7（MySQL 8.0 协议兼容） |
| 构建运行 | Maven / Spring Boot 3.3.13 / JDK 17 |

---

## 七、安全说明

- 默认账号用 `root`，生产请按 `sql/tidb_account.sql` 注释创建专用账号（最小权限）
- 密码通过环境变量 `DB_USERNAME` / `DB_PASSWORD` 注入，配置文件不含明文
- **无登录鉴权**，仅限内网/本机验证；公网部署请接入 Spring Security + JWT
- `spring.sql.init.mode=always` 是为了 Demo 自动建表，生产请改为 `never`，改由 DBA 执行审核过的脚本
