# Ruoyi_demo

一个**零第三方 Web 框架**的 Java 增删改查（CRUD）Demo：JDK 自带 `HttpServer` + JDBC + 原生 HTML/CSS/JS，
后端对接 **TiDB v8.5.7**（MySQL 协议），带一个内嵌的 Web 管理页面，可对真实数据库做增、删、改、查、事务转账与只读 SQL 查询。

> 本仓库是部署验证 Demo 的**源码归档**。代码保留了当时在服务器上真实运行过的版本，未做重构，
> 目的有二：①作为可复现的最小 CRUD 样例；②作为迁移到 RuoYi（若依）技术栈时的对照底稿。
> 完整技术栈说明见 **[Ruoyi技术框架一览.md](./Ruoyi技术框架一览.md)**。

---

## 一、特性一览

| 能力 | 说明 |
|---|---|
| 列表 / 分页 / 搜索 | `LIMIT ? OFFSET ?` + `name LIKE ? OR id LIKE ?`，返回总数用于分页 |
| 新增 | `INSERT INTO account (name, balance) VALUES (?,?)` |
| 编辑 | `UPDATE account SET name=?, balance=? WHERE id=?` |
| 删除 | `DELETE FROM account WHERE id=?`（页面二次确认） |
| 转账 | 事务：先扣（带 `balance >= ?` 条件）再加，失败 `ROLLBACK` |
| 聚合统计 | `COUNT/SUM/MAX/MIN` |
| 只读 SQL 控制台 | 白名单仅放行 `SELECT/SHOW/EXPLAIN/DESC`，最多 200 行 |
| 自举 bootstrap | 首次启动自动建库、建账号、建表（`CREATE TABLE IF NOT EXISTS`） |
| 一次性验证 | `once` 模式跑 12 项端到端检查并打印报告 |

默认只监听 `127.0.0.1:18080`，公网/内网 IP 都访问不到，需通过 SSH 本地转发访问。

---

## 二、目录结构

```
Ruoyi_demo/
├── pom.xml                                    # Maven 工程（唯一依赖：mysql-connector-j 8.4.0）
├── src/main/java/com/example/tidbdemo/
│   ├── Main.java          # 入口：bootstrap / once(验证) / serve(服务)
│   ├── Config.java        # properties 配置 + ${ENV:VAR} 占位符解析
│   ├── Db.java            # JDBC 连接（DriverManager，无连接池）
│   ├── AccountRepo.java   # 数据访问：CRUD / 搜索 / 统计 / 建表
│   ├── HttpApi.java       # JDK HttpServer 路由 + REST 接口
│   ├── Json.java          # 手写 JSON 序列化/反序列化与转义
│   └── Report.java        # 单行 / 多行查询辅助
├── src/main/resources/webui/index.html        # 内嵌 Web 管理页面（HTML+CSS+原生 JS，无框架无构建）
├── deploy/
│   ├── app.properties.example                 # 应用配置模板（密码用环境变量占位）
│   ├── app.env.example                        # systemd EnvironmentFile 模板
│   ├── tidb-demo.service                      # systemd 单元
│   ├── open-ui.sh                             # 本机一键建 SSH 隧道并打开页面
│   └── close-ui.sh                            # 关闭本机隧道
├── Ruoyi技术框架一览.md                        # 技术栈全景 + 与 RuoYi 逐层对照 + 迁移建议
└── .gitignore
```

---

## 三、快速开始

### 3.1 前置条件

- **构建机**：JDK 17+、Maven 3.6+（本项目用 JDK 17 / Maven 3.9.16 构建）
- **数据库**：TiDB 或 MySQL（TiDB v8.5.7 已实测，MySQL 协议兼容）；库名默认 `demo_db`
- **运行机**：JDK 17（无需 Maven，fat jar 已含全部依赖）

### 3.2 构建

```bash
mvn -q -DskipTests package
# 产物：target/tidb-demo.jar（fat jar，约 4.3MB）
```

### 3.3 配置

```bash
cp deploy/app.properties.example app.properties
cp deploy/app.env.example        app.env
vi app.properties   # 改 jdbc.url / jdbc.user
vi app.env          # 填 TIDB_DEMO_PASSWORD
chmod 600 app.properties app.env
```

`app.properties` 中密码写作 `${ENV:TIDB_DEMO_PASSWORD}`，运行时由环境变量注入，jar 与配置文件里都没有明文。
`TIDB_BOOTSTRAP_ROOT_PASSWORD` 用于首次建库建账号（TiDB 初始 root 无密码时留空）。

### 3.4 运行

```bash
# 服务模式（常驻，监听 127.0.0.1:18080）
java -Dconfig=app.properties -jar target/tidb-demo.jar serve

# 一次性验证（跑 12 项检查后退出，不改常驻状态）
java -Dconfig=app.properties -jar target/tidb-demo.jar once
```

浏览器打开 <http://127.0.0.1:18080/>。若在远端服务器上运行，用本机隧道：

```bash
ssh -f -N -L 18080:127.0.0.1:18080 <user@host>
```

### 3.5 部署为 systemd 服务

```bash
sudo useradd -r -s /sbin/nologin -d /opt/tidb-demo tidbdemo
sudo mkdir -p /opt/tidb-demo/logs
sudo cp target/tidb-demo.jar app.properties app.env /opt/tidb-demo/
sudo cp deploy/tidb-demo.service /etc/systemd/system/
sudo chown -R tidbdemo:tidbdemo /opt/tidb-demo
sudo systemctl daemon-reload
sudo systemctl enable --now tidb-demo
```

---

## 四、HTTP 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/`、`/index.html`、`/ui` | 管理页面 |
| GET | `/api/info` | TiDB 版本、库名、TiKV store 状态 |
| GET | `/api/accounts?keyword=&limit=&offset=` | 分页列表，含 `total` |
| GET | `/api/accounts?id=N` | 单条查询 |
| POST | `/api/accounts` `{name,balance}` | 新增 |
| PUT | `/api/accounts` `{id,name,balance}` | 修改 |
| DELETE | `/api/accounts` `{id}` | 删除 |
| POST | `/api/transfer` `{from,to,amount}` | 事务转账，余额不足回滚 |
| GET | `/api/stats` | 聚合统计 |
| POST | `/api/query` `{sql}` | 只读 SQL（白名单校验，≤200 行） |

兼容旧路径：`/health`、`/accounts`、`/transfer`、`/stats`。

curl 示例：

```bash
curl -s http://127.0.0.1:18080/api/info
curl -s -X POST http://127.0.0.1:18080/api/accounts -d '{"name":"carol","balance":500}'
curl -s -X POST http://127.0.0.1:18080/api/transfer -d '{"from":1,"to":2,"amount":10}'
```

---

## 五、技术栈速览

| 层 | 技术 |
|---|---|
| 前端 | HTML5 + CSS3（自定义属性 / Grid / Flex）+ 原生 ES6+ JS（fetch、Promise、DOM），**无框架无构建** |
| 接口 | JDK 内置 `com.sun.net.httpserver.HttpServer`（8 线程池） |
| 业务 | 纯 Java 手写（无 Spring） |
| 数据访问 | JDBC 4.3 + `mysql-connector-j` 8.4.0，手动事务 |
| 存储 | TiDB v8.5.7（PD/TiKV/TiDB），MySQL 8.0 协议兼容 |
| 构建 | Maven 3.9.16 + maven-shade-plugin 打 fat jar |
| 部署 | systemd（EnvironmentFile 注入密钥、Restart=on-failure） |

> 详细说明、与 RuoYi 的逐层对照以及迁移改造清单，见 **[Ruoyi技术框架一览.md](./Ruoyi技术框架一览.md)**。

---

## 六、安全说明

- 默认只绑定 `127.0.0.1`，不对外暴露
- 密码不落盘：配置文件存 `${ENV:VAR}` 占位符，实际值由 systemd `EnvironmentFile`（chmod 600）注入
- 只读 SQL 接口有服务端白名单，写操作（INSERT/UPDATE/DELETE/DROP）被拒绝
- 应用账号 `demo` 仅授权 `127.0.0.1`/`localhost`
- **本 Demo 不含登录鉴权**，仅供内网/本机验证，生产请接入 Spring Security + JWT（见技术文档第 8 节）
