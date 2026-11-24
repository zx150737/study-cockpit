# 学习监控驾驶舱 · Study Cockpit

基于 Vue 3、Spring Boot 和 MySQL 的学习计划与复盘网页应用。
管理目标和科目、安排每日任务、记录完成情况，通过驾驶舱与周报查看学习进度。

## 功能

- 目标与科目管理：设置科目默认权重。
- 每日任务：创建、编辑、筛选、完成标记及逾期提醒。
- 图片记录：上传学习图片，登录鉴权访问，15 天到期清理。
- 驾驶舱：当日完成率、本周趋势、连续打卡、目标进度及积压任务。
- 周度分析：科目统计、周报 CSV 导出、历史周结算与归档。
- 用户账号：账号密码登录，业务数据按用户隔离。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Element Plus、ECharts、Pinia |
| 后端 | Java 17、Spring Boot 3.2.5、Spring Security、MyBatis-Plus |
| 数据库 | MySQL 8.0.16+ |
| 认证 | BCrypt、JWT HttpOnly Cookie、CSRF 双提交 |
| 部署 | Nginx、HTTPS、systemd；上传文件存储在 Web 根目录之外 |

## 项目目录

```text
backend/     Java 后端源码与 Maven 配置
frontend/    Vue 网页源码与 pnpm 锁文件
docs/        架构设计、部署说明、SQL 建表脚本
deploy/      环境变量模板、Nginx/systemd 配置和初始化脚本
```

本仓库仅包含网页版本。没有内置账号、用户数据、数据库备份、真实密钥或微信小程序代码。

## 本地启动

准备 JDK 17、Maven 3.9+、Node.js 22、pnpm 和 MySQL 8。

### 1. 初始化数据库

在全新的业务库环境执行 `docs/sql/schema.sql`，并创建自己的数据库连接账号。
脚本用于首次建表，不是现有数据库的升级迁移，不应对已有业务库重复执行。
创建与授权数据库账号的示例见 [部署文档](docs/02-部署文档.md)。

### 2. 启动后端

后端默认采用 dev profile。配置以下环境变量后启动：

| 变量 | 本地用途 |
| --- | --- |
| `DB_USERNAME` | 自己创建的数据库账号 |
| `DB_PASSWORD` | 对应数据库密码 |
| `DB_URL` | 可选；默认连接本机 3306 端口的 study_cockpit |
| `FILE_STORAGE_DIR` | 上传目录的绝对路径；Windows 必须设为实际盘符路径 |
| `APP_ALLOW_REGISTER` | 首次建号时临时设为 true，建号后改回 false 并重启 |

PowerShell 示例（替换占位内容）：

```powershell
$env:DB_USERNAME = 'your_db_user'
$env:DB_PASSWORD = 'your_db_password'
$env:FILE_STORAGE_DIR = Join-Path $PWD '.local/uploads'
$env:APP_ALLOW_REGISTER = 'true'
cd backend
mvn spring-boot:run
```

默认后端地址为 `http://127.0.0.1:8080`。dev 模式可自动生成临时签名密钥，重启后原会话失效。
生产环境必须自行生成并配置持久化随机密钥，参见 `deploy/env.example`。

### 3. 创建自己的登录账号

项目不提供通用默认用户名或密码。可按部署文档的“初始化登录账号”章节，先获取 CSRF Cookie，再携带 Cookie 和同值的 `X-XSRF-TOKEN` 请求头调用 `/api/auth/register`。
也可使用 `deploy/scripts/init-user.sh` 生成 BCrypt 密文及待执行 SQL。
账号建立后关闭 `APP_ALLOW_REGISTER` 并重启后端。

### 4. 启动网页

在另一个终端中运行：

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

打开 `http://127.0.0.1:5173`。Vite 将 `/api` 请求代理到本机 8080 端口。
如果 pnpm 提示依赖构建脚本未获批准，可按提示运行 `pnpm approve-builds`，审阅并批准需要的依赖构建脚本后重新安装。

## 检查与构建

```sh
# frontend 目录
pnpm typecheck
pnpm build

# backend 目录
mvn clean package
```

前端产物位于 `frontend/dist/`，后端产物位于 `backend/target/`，均不提交到源码仓库。

## 服务器部署

详细步骤见 [部署文档](docs/02-部署文档.md)。示例模板需要替换为自己的域名、目录和数据库连接信息。
生产部署使用 prod profile、HTTPS、独立随机密钥及最小权限数据库账号，关闭首次初始化用的注册入口。

GitHub 仓库用于分享源码；将代码开源不会自动把 Java 后端、数据库或网页部署到公网。

## 当前边界

- 本地单实例实现：限流、登录锁定和 JWT 黑名单使用进程内缓存；重启会清除相关缓存，多实例部署需要额外设计。
- 周结算默认在北京时间每周日执行；首次部署时历史周报与学习基线可能为空。
- 邮件通知和对象存储仅预留接口，未实现发送服务或云存储接入。
- 架构文档保留了早期设计内容，当前实现以源码为准。
- 公开源码不代表完成生产环境安全审计；部署前应审阅依赖版本、配置和备份策略。

## 许可证

采用 [MIT License](LICENSE)。允许使用、修改、分发和商用，需保留版权与许可声明。
第三方依赖仍适用其各自许可证。
