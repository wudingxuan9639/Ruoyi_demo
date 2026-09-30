# Ruoyi 技术框架一览

> 适用对象：本仓库 `Ruoyi_demo`（一个真实跑在 TiDB 上的 Java CRUD Demo）
> 文档目的：把 **本 Demo 实际用到的前后端技术** 讲透，并与 **RuoYi（若依）官方技术栈** 做逐层对照，
> 最后给出从本 Demo 演进到 RuoYi 的替换清单。
> 编写日期：2026-09-30

---

## 0. 一句话定位

| | 本 Demo | RuoYi |
|---|---|---|
| 定位 | 最小可运行验证程序：证明「应用 → TiDB」链路真通，能对真实库做增删改查 | 企业级后台管理快速开发平台 |
| 依赖数量 | **1 个**（mysql-connector-j） | 数十个（Spring 全家桶 + 前端生态） |
| 代码规模 | 约 1200 行 | 数万行 |
| 是否需要 Node/Vue/前端构建 | **否** | 是 |
| 适合 | 学习原理、验证环境、做迁移底稿 | 真实业务系统开发 |

**结论**：本 Demo 不是 RuoYi，而是"把 RuoYi 里那些框架全部拿掉之后，剩下的骨架"。
它的价值在于：让你看清每一层框架究竟替你做了什么，迁移到 RuoYi 时只替换对应层即可。

---

## 一、技术全景

```
┌──────────────────────────────────────────────────────────────┐
│ 展示层  HTML5 + CSS3 + 原生 JavaScript(ES6+)                  │
│         index.html 单文件，内嵌于 jar，无框架 / 无构建 / 无 UI 库│
└───────────────┬──────────────────────────────────────────────┘
                │  fetch (REST / JSON)
┌───────────────▼──────────────────────────────────────────────┐
│ 接口层  com.sun.net.httpserver.HttpServer（JDK 自带）          │
│         127.0.0.1:18080，8 线程池，手写路由表                  │
├──────────────────────────────────────────────────────────────┤
│ 业务层  纯 Java：转账事务、参数校验、SQL 白名单                 │
├──────────────────────────────────────────────────────────────┤
│ 数据层  JDBC 4.3 + mysql-connector-j 8.4.0（DriverManager）    │
│         手写 JSON 序列化、properties + ${ENV:} 配置             │
└───────────────┬──────────────────────────────────────────────┘
                │  MySQL 8.0 协议
┌───────────────▼──────────────────────────────────────────────┐
│ 存储层  TiDB v8.5.7  tidb-server(4000) / pd-server(2379)      │
│                      / tikv-server(20160)                     │
└──────────────────────────────────────────────────────────────┘

构建：Maven 3.9.16 + maven-shade-plugin → fat jar
运行：JDK 17 + systemd（EnvironmentFile 注入密钥）
```

---

## 二、后端技术明细

| 技术 | 版本 | 在本 Demo 中的作用 | 对应代码 |
|---|---|---|---|
| **Java SE（JDK）** | 17（LTS） | 语言与运行时；`maven.compiler.release=17` | 全部 |
| **Maven** | 3.9.16 | 依赖管理、编译、打包 | `pom.xml` |
| **maven-shade-plugin** | 3.6.0 | 打 fat jar（含依赖 + Main-Class），并用 `ServicesResourceTransformer` 合并 SPI | `pom.xml` |
| **mysql-connector-j** | 8.4.0 | **唯一第三方依赖**，MySQL/TiDB 的 JDBC 驱动 | `pom.xml` |
| **JDBC 4.3（`java.sql`）** | JDK 内置 | 连接、预编译语句、事务、元数据 | `Db.java` / `AccountRepo.java` |
| **`com.sun.net.httpserver.HttpServer`** | JDK 内置 | HTTP 服务与路由，**零第三方 Web 框架** | `HttpApi.java` |
| **手写 JSON** | 自实现 | 序列化/反序列化 + 转义（`Json.esc`） | `Json.java` |
| **properties + `${ENV:VAR}`** | 自实现 | 配置外置，密码从环境变量注入 | `Config.java` |
| **ExecutorService** | JDK 内置 | `Executors.newFixedThreadPool(8)` 处理并发请求 | `HttpApi.java` |

### 2.1 为什么选 JDK 自带 HttpServer

- 服务器**完全无外网**，无法在线拉 Spring Boot 依赖；离线镜像只准备了 TiDB 相关组件
- 只要证明数据库链路可用，不需要 MVC、IoC、AOP 这些能力
- 好处：产物 4.3MB、启动秒级、无框架版本冲突；代价：路由、参数解析、JSON、异常处理全部要手写

### 2.2 关键实现点

```java
// 只绑回环地址 + 线程池 + 手写路由表
this.server = HttpServer.create(new InetSocketAddress(host, port), cfg.getInt("http.backlog", 64));
this.pool   = Executors.newFixedThreadPool(8);
server.setExecutor(pool);

private void route(String path, HttpHandler h) {   // HttpServer 无法按路径回取 handler，故自存一份
    routes.put(path, h);
    server.createContext(path, h);
}
```

- **连接**：`DriverManager` 直连，无连接池；`connectTimeout=5000`、`socketTimeout=20000`
- **事务**：转账用 `setAutoCommit(false)` → 条件扣款 → 加款 → `commit()`，余额不足 `rollback()`
- **防注入**：全部使用 `PreparedStatement` 占位符
- **只读保护**：`/api/query` 校验 SQL 前缀白名单（`SELECT/SHOW/EXPLAIN/DESC`），并限制最多 200 行

---

## 三、前端技术明细

前端**只有一个文件**：`src/main/resources/webui/index.html`（313 行），打包时进入 jar，由后端直接托管，
**不需要 nginx、不需要额外端口、不需要 Node**。

| 技术 | 用法 |
|---|---|
| **HTML5** | 语义化结构：统计卡片、工具栏、表格、模态框、Toast、只读 SQL 控制台 |
| **CSS3 自定义属性（CSS 变量）** | `:root` 定义主题色 `--brand/--ok/--danger/--warn/--bg/--card/--line/--text/--muted`，改一处即换肤 |
| **Flexbox / Grid 布局** | `.stats{display:grid;grid-template-columns:repeat(4,1fr)}`、`.row2` 三列表单、工具栏自适应换行 |
| **原生 JavaScript（ES6+）** | `const/let`、模板字符串、箭头函数、Promise |
| **Fetch API** | 统一封装 `fetch(path, opt)`，与后端 REST 交互（无 Axios） |
| **DOM 操作** | `querySelectorAll("[data-edit]")` 批量绑定行内按钮（事件委托思路）、`innerHTML` 渲染表格 |
| **模态框 / Toast** | 纯 CSS + JS class 切换（`.mask.on`、`.toast.on`），无 UI 库 |
| **无框架、无构建** | 没有 Vue/React、没有 Vite/Webpack、没有 Element UI，双击 jar 就能跑 |

页面能力：统计卡片（账户数/余额合计/最高/最低）、分页 + 关键字搜索、新增/编辑模态框、删除确认、转账表单、只读 SQL 查询框。

> 这正是与 RuoYi 差别最大的一层：RuoYi 的前端是完整工程（Vue + 组件库 + 路由 + 状态管理 + 构建链），
> 本 Demo 用 313 行原生代码实现了同类的一个页面。

---

## 四、数据层：TiDB

| 项 | 值 |
|---|---|
| 版本 | TiDB **v8.5.7**（TiUP 源当时最高版本） |
| 组件 | `tidb-server` 4000（MySQL 协议）、`pd-server` 2379（调度与 TSO）、`tikv-server` 20160（存储） |
| 部署形态 | 单机开发测试：各组件 1 实例，PD 设 `replication.max-replicas=1`（单 TiKV 必须，否则集群永不健康） |
| 兼容性 | 高度兼容 **MySQL 8.0 协议与语法**，现有 MySQL 驱动/ORM 基本可直接连 |
| 事务 | 分布式事务（Percolator 两阶段提交），Demo 的转账用它验证真实提交与回滚 |
| 索引与执行计划 | `idx_name`；`EXPLAIN` 显示 `IndexLookUp → IndexRangeScan + TableRowIDScan(cop[tikv])` |
| HTAP | 行存 TiKV 做 OLTP，可选 TiFlash 列存做分析（本 Demo 未启用） |

**TiDB 与 MySQL 的主要差异（迁移时必读，具体以官方文档为准）**：

- 不支持存储过程、触发器、自定义函数、物化视图；普通视图只读
- 外键约束支持有限，建议由应用层保证引用完整性
- `AUTO_INCREMENT` 只保证全局唯一与递增趋势，**不保证连续**（多节点各分配一段）
- 单事务有数据量上限（默认配置下约 100MB 量级），禁止超大批量写入放在一个事务里
- 部分 DDL 为在线变更，行为与 MySQL 有差异；大表 DDL 需评估
- 全文索引、`SELECT ... INTO OUTFILE`、部分系统表/变量与 MySQL 不同

---

## 五、构建、部署与运维

| 环节 | 技术 | 说明 |
|---|---|---|
| 构建 | Maven + shade | `mvn -q -DskipTests package` → `target/tidb-demo.jar` |
| 配置 | `app.properties` + `${ENV:VAR}` | 密码不落盘 |
| 密钥注入 | systemd `EnvironmentFile`（chmod 600） | 避免命令行参数泄露、避免特殊字符转义问题 |
| 进程管理 | systemd | `Type=simple`、`Restart=on-failure`、`TimeoutStopSec=15`、日志追加到文件 |
| 运行账号 | `tidbdemo`（nologin 系统用户） | 降权运行 |
| 访问方式 | SSH 本地转发 `ssh -L 18080:127.0.0.1:18080` | 服务只监听回环地址，公网不可达 |
| 日志 | `/opt/tidb-demo/logs/app.log` | systemd 标准输出/错误重定向 |

---

## 六、RuoYi（若依）官方技术栈

RuoYi 是一套**前后端分离**的企业级快速开发平台，官方主线为 `RuoYi-Vue`（Spring Boot + Vue）。
以下依据官方站点与文档整理（版本分支可能随官方更新而变，以 <https://doc.ruoyi.vip> 为准）。

### 6.1 后端

| 分类 | 技术 | 作用 |
|---|---|---|
| 核心框架 | **Spring Boot** 2.x / 3.x / 4.x（分支并行维护） | 自动配置、内嵌容器、项目骨架 |
| IoC / AOP / 事务 | Spring Framework | 依赖注入、切面、声明式事务 |
| 安全 | **Spring Security** + **JWT** | 登录认证、RBAC 权限、无状态令牌 |
| 持久层 | **MyBatis**（社区增强版常用 MyBatis-Plus） | ORM、XML/注解 SQL |
| 连接池 | Alibaba **Druid** | 数据源、监控、慢 SQL 统计 |
| 数据库 | MySQL 为主（社区版支持 Oracle/PG/达梦/**TiDB** 等） | 数据存储 |
| 缓存 | **Redis**（+ Redisson） | 会话、字典、权限、限流缓存 |
| 分页 | PageHelper | MyBatis 物理分页 |
| 定时任务 | **Quartz** | 在线任务调度，支持集群 |
| 接口文档 | Knife4j / Swagger | 自动生成 API 文档与在线调试 |
| 工具库 | Hutool、Lombok、fastjson2、Apache POI、Velocity | 通用工具、简化代码、JSON、Excel、模板 |
| 校验 | Hibernate Validator | 参数校验 |
| 构建 | Maven | 多模块管理 |

### 6.2 前端

| | **RuoYi-Vue2** | **RuoYi-Vue3**（官方主推） |
|---|---|---|
| 框架 | Vue 2 | Vue 3 |
| 构建工具 | Vue CLI（Webpack） | **Vite** |
| UI 组件库 | **Element UI** | **Element Plus** |
| 状态管理 | Vuex 3 | **Pinia** |
| 路由 | Vue Router 3 | Vue Router 4 |
| 网络请求 | Axios | Axios |
| 脚本语言 | JavaScript | JavaScript（亦可用 TypeScript） |
| 特点 | 经典稳定、资料多 | 现代栈、体验与性能更好 |

后端与前端可混搭，例如后端 Spring Boot 2.x + 前端 RuoYi-Vue3。

### 6.3 RuoYi 家族其他版本（供选型）

| 版本 | 技术要点 | 适用 |
|---|---|---|
| **RuoYi**（不分离版） | Spring Boot + Shiro + Thymeleaf + Bootstrap | 单体、页面服务端渲染 |
| **RuoYi-Vue** | Spring Boot + Spring Security + JWT + MyBatis + Vue | **最常用**，前后端分离 |
| **RuoYi-Cloud** | Spring Cloud & Alibaba（Nacos / Gateway / Sentinel / Feign） | 微服务、多模块 |
| **RuoYi-App** | UniApp + Vue | 移动端 |
| **RuoYi-Vue-Plus / ruoyi-vue-pro**（社区） | MyBatis-Plus、多租户、工作流 Flowable、支付/短信等 | 需要开箱即用的更多能力 |

### 6.4 内置功能模块（了解"框架替你做了什么"）

用户管理、部门管理、岗位管理、菜单管理、角色与按钮级授权、数据权限、字典管理、参数管理、
通知公告、操作日志、登录日志、在线用户、定时任务、代码生成、系统接口、服务监控等。

---

## 七、本 Demo ↔ RuoYi 逐层对照（迁移映射表）

| 层 | 本 Demo | RuoYi 对应 | 迁移要点 |
|---|---|---|---|
| HTTP 服务 | `HttpServer` + 手写路由 | Spring Boot 内嵌 Tomcat + `@RestController`/`@RequestMapping` | 删掉 `HttpApi` 路由表，改为 Controller |
| 参数解析 / JSON | 手写 `Json` | Jackson / fastjson2 + `@RequestBody` | 删掉 `Json.java` |
| 业务服务 | 直接在 handler 里写 | `Service` 层（接口 + 实现）+ `@Transactional` | 抽出 `AccountService` |
| 数据访问 | `AccountRepo` 手写 JDBC | `Mapper`（XML/注解）+ 实体 `domain` + `PageHelper` | SQL 基本可复用，改为 Mapper 方法 |
| 事务 | 手动 `commit/rollback` | `@Transactional` 声明式事务 | 转账方法加注解即可 |
| 数据源 | `DriverManager` 直连 | Druid / HikariCP 数据源 | 必须加连接池（生产刚需） |
| 配置 | `app.properties` + `${ENV:}` | `application.yml` + `@ConfigurationProperties`（或 Nacos） | 配置项平移 |
| 建库建表 | `Main.bootstrap` 里执行 DDL | `sql/*.sql` 初始化脚本 + Flyway/Liquibase | DDL 抽到脚本 |
| 前端页面 | `index.html` 原生实现 | Vue3 页面 + Element Plus（表格/表单/弹窗/分页）+ Axios + Pinia | 逻辑不变，重写视图层 |
| 鉴权 | **无** | Spring Security + JWT + RBAC | 生产必须补 |
| 日志 | 追加到 `app.log` | Logback + 操作日志/登录日志切面 | 接入日志框架 |
| 部署 | systemd + fat jar | 同样 jar 部署，或 Docker / K8s | 单元文件可复用 |

**不变的部分**：数据库表结构、SQL、TiDB 连接方式（RuoYi 官方使用 MySQL 协议，TiDB 兼容，改 JDBC URL 即可）。

---

## 八、迁移到 RuoYi 的改造清单（可直接照做）

1. **引框架**：新建 Spring Boot 工程（或直接用 RuoYi-Vue 模板），加 `spring-boot-starter-web`
2. **接 TiDB**：配置 Druid 数据源，`jdbc:mysql://<host>:4000/<db>`；按第四节差异清单规避不兼容特性
3. **换 ORM**：引入 MyBatis（或 MyBatis-Plus）+ PageHelper；把 `AccountRepo` 的 SQL 迁到 Mapper
4. **拆分层**：`Controller`（接参数）→ `Service`（业务 + `@Transactional`）→ `Mapper`（SQL）
5. **重写前端**：Vue3 + Vite + Element Plus + Axios + Pinia；`el-table` 做列表/分页、`el-dialog` 做新增编辑、`el-message` 替代 Toast
6. **补安全**：Spring Security + JWT，登录、菜单权限、按钮级权限、数据权限
7. **加缓存**：Redis 存字典/权限/会话，减少 DB 压力
8. **日志与监控**：Logback、操作日志切面、Druid 慢 SQL、Spring Boot Actuator
9. **用代码生成**：RuoYi 的代码生成功能可直接生成 account 模块的 Controller/Service/Mapper/Vue/SQL，与本 Demo 逻辑对照学习
10. **部署**：jar + systemd（或容器化），Nginx 反向代理前端静态资源

---

## 九、本次验证环境

| 项 | 值 |
|---|---|
| 操作系统 | Kylin V10（内核 4.19.90，glibc 2.28） |
| CPU / 内存 | 海光 16 核 / 62 GiB（KVM 虚拟机） |
| JDK | 17（`/opt/jdk-17`，tarball 安装） |
| 数据库 | TiDB v8.5.7，组件均绑定 `127.0.0.1`（4000 / 2379 / 20160） |
| 应用 | systemd `tidb-demo.service`，监听 `127.0.0.1:18080` |
| 构建机 | macOS + JDK 17 + Maven 3.9.16（fat jar 为跨平台字节码，无需在服务器构建） |
| 服务器网络 | **无外网**，依赖全部离线准备 |

---

## 十、参考链接

- RuoYi 官网：<https://ruoyi.vip>
- RuoYi 文档：<https://doc.ruoyi.vip/ruoyi-vue>
- RuoYi-Vue 源码（Gitee）：<https://gitee.com/y_project/RuoYi-Vue>
- TiDB 官方文档：<https://docs.pingcap.com/tidb/stable/overview>
- TiDB 与 MySQL 兼容性：<https://docs.pingcap.com/tidb/stable/mysql-compatibility>
- MySQL Connector/J：<https://dev.mysql.com/doc/connector-j/en/>
