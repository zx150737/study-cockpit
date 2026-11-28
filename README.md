# 学习监控驾驶舱 · Study Cockpit

新增 **Cloudflare Pages + Supabase 免费部署方式**：邮箱密码登录，账号数据独立，使用平台网址。请先阅读 [免费版部署指南](免费版部署指南.md)。线上地址：<https://study-cockpit.pages.dev>（当前为 v2.0.0）；下文的 Java/MySQL 部署方式继续保留。

基于 Vue 3、Spring Boot 和 MySQL 的学习计划与复盘网页应用。
管理目标和科目、安排每日任务、记录完成情况，通过驾驶舱与周报查看学习进度。

## 功能

- 目标与科目管理：设置科目默认权重。
- 每日任务：创建、编辑、筛选、完成标记及逾期提醒。
- 图片记录：上传学习图片，登录鉴权访问，15 天到期清理。
- 驾驶舱：当日完成率、本周趋势、连续打卡、目标进度及积压任务。
- 周度分析：科目统计、周报 CSV 导出、历史周结算与归档。
- AI 学习助手：贴题获得分步讲解、考点与变式练习；一键加入错题本。
- 错题复习：按遗忘曲线（1/2/4/8/16 天）安排复习，自评三档调整下次到期。
- 用户账号：自助注册、账号密码登录与修改密码，业务数据按用户隔离；注册入口由服务器开关控制。

## AI 学习助手（仅云端版）

云端版新增两个页面：`/tutor`（提问与讲解）与 `/review`（错题复习）。设计取舍是**只做讲解与练习，不做代做**：

- 只发送用户自己输入或粘贴的题面文本；不上传整页截图、不读取课程账号、不碰任何验证码。
- 讲解以「思路 → 步骤 → 结论 → 变式练习」返回；练习的答案默认折叠，让使用者先自己算。
- 作答、提交、判分仍由使用者本人在平台完成。

数据在 `sc_ai_threads`（提问与结构化答复）和 `sc_ai_cards`（复习卡）两张表；答复列由边缘函数用 service_role 写入，客户端伪造会被 `sc_ai_guard` 拒绝。

模型调用发生在边缘函数 `supabase/functions/ai-tutor`，密钥只存在服务端环境变量里，前端与数据库都拿不到。配置一次即可：

```text
Supabase 控制台 → Project Settings → Edge Functions → Secrets → 新增
  DEEPSEEK_API_KEY = sk-...      （必填）
  DEEPSEEK_MODEL   = deepseek-chat（可选，默认即此值）
```

未配置密钥时，页面会明确提示「管理员尚未配置 AI 密钥」，不会静默失败。每个账号每天有 60 次讲解额度上限（`ai-tutor` 里的 `DAILY_LIMIT`），避免共享密钥被刷爆。

| 文件 | 作用 |
| --- | --- |
| `supabase/migrations/202611280001_tutor.sql` | 两张表、RLS、守卫触发器、`sc_ai_read` / `sc_ai_write` |
| `supabase/functions/ai-tutor/index.ts` | 校验登录态与归属、配额、调用模型、写入答复 |
| `frontend/src/views/tutor/TutorView.vue` | 提问与讲解渲染 |
| `frontend/src/views/tutor/ReviewView.vue` | 复习队列与卡片管理 |
| `frontend/tests/tutor-db.test.mjs` | 用 PGlite 跑迁移并验证权限与复习算法 |

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
compose.yaml 三服务部署配置（HTTPS 网页、Java 后端、MySQL）
Dockerfile   从源码构建前后端部署镜像
```

本仓库仅包含网页版本。没有内置账号、用户数据、数据库备份、真实密钥或微信小程序代码。

## 本地启动

准备 JDK 17、Maven 3.9+、Node.js 22、pnpm 11.7.0 和 MySQL 8。前端通过 `packageManager` 固定 pnpm 版本，CI 使用相同版本。

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
| `APP_ALLOW_REGISTER` | true 开放自助注册；false 关闭注册但允许已有账号登录 |

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

项目不提供通用默认用户名或密码。`APP_ALLOW_REGISTER=true` 时，打开网页点击“创建账号”，填写用户名、密码和可选昵称即可注册。每个账号独立保存学习数据。
若只供指定人员使用，可建号后关闭注册开关并重启；已有账号可继续登录。也可使用 `deploy/scripts/init-user.sh` 生成 BCrypt 密文及待执行 SQL。

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
pnpm install --frozen-lockfile
pnpm typecheck
pnpm lint
pnpm build

# backend 目录
mvn clean verify
```

前端产物位于 `frontend/dist/`，后端产物位于 `backend/target/`，均不提交到源码仓库。

## 账号与密码

- 首次部署按部署文档创建账号；登录后可在页面右上角「修改密码」更新口令。
- 修改密码需校验当前密码；密码和会话版本在同一条数据库更新中写入，成功后该账号的旧登录态全部失效，需重新登录。
- 需要用户自助注册时设 `APP_ALLOW_REGISTER=true`；关闭后网页隐藏入口，接口也拒绝注册。

## 让其他人通过网址使用

推荐使用 [多人使用与服务器部署](docs/03-多人使用与服务器部署.md) 中的 Docker Compose 方案。它将 HTTPS 网页、Java 后端、MySQL 一起部署，数据库与图片使用持久卷，并提供随机密钥初始化与备份脚本。

部署到已准备好域名和 Docker 的全新 Linux 服务器：

```sh
bash deploy/docker/setup.sh study.example.com
docker compose up -d --build --wait
```

将示例域名替换为自己的域名，并提前解析到服务器。服务器费用、注册规则、数据备份与上线验收见部署文档；源码包本身不会创建公网服务器。

## 服务器部署

详细步骤见 [部署文档](docs/02-部署文档.md)。示例模板需要替换为自己的域名、目录和数据库连接信息。
生产部署使用 prod profile、HTTPS、独立随机密钥及最小权限数据库账号，按是否开放自助注册配置注册开关。

GitHub 仓库用于分享源码；将代码开源不会自动把 Java 后端、数据库或网页部署到公网。

## 当前边界

- 本地单实例实现：限流、登录锁定和 JWT 黑名单使用进程内缓存；重启会清除相关缓存，多实例部署需要额外设计。
- 周结算默认在北京时间每周日执行；首次部署时历史周报与学习基线可能为空。
- 邮件通知和对象存储仅预留接口，未实现发送服务或云存储接入。
- 架构文档保留了早期设计内容，当前实现以源码为准。
- 公开源码不代表完成生产环境安全审计；部署前应审阅依赖版本、配置和备份策略。

## 备份

数据库需定期备份，图片 15 天自动过期无需长期归档。可使用 `deploy/scripts/backup-db.sh`，配合 cron 每日执行，默认保留 14 天备份。脚本先生成临时文件，压缩校验通过后才发布备份并清理过期文件；备份失败会保留既有备份。配置与恢复示例见部署文档「备份、升级与回滚」章节。

## 许可证

采用 [MIT License](LICENSE)。允许使用、修改、分发和商用，需保留版权与许可声明。
第三方依赖仍适用其各自许可证。
