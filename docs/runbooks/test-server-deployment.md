# 测试服务器部署手册（纯 HTTP 双主机）

本文档描述把原型管理平台部署到公司内网测试服务器（纯 HTTP）的完整步骤。

## 1. 前置条件

| 项目 | 要求 |
| --- | --- |
| 服务器 | 公司内网 Linux x86_64/arm64，可运行 Docker Engine + Compose v2 |
| 域名 | 两个受控子域（示例 `prototype.corp.test`、`preview.corp.test`）；无内网 DNS 时在测试机 `hosts` 配置同一 IP 两个名称 |
| 端口 | 80（Nginx 对外）、9090（Prometheus，仅绑定 127.0.0.1） |
| 工具 | git、Docker、curl、jq |

`/etc/hosts` 示例（无 DNS 时）：

```text
10.0.0.8 prototype.corp.test
10.0.0.8 preview.corp.test
```

## 2. 构建与启动

```bash
git clone <repo> prototypeManage && cd prototypeManage

# 前端依赖（锁文件）
corepack enable
pnpm install --frozen-lockfile

# 配置环境变量
cp .env.example .env
# 编辑 .env：BOOTSTRAP_ADMIN_USERNAME/PASSWORD、MYSQL_*、MINIO_ROOT_*、
# UPLOAD_PUBLIC_BASE_URL=http://prototype.corp.test/upload-objects、
# PREVIEW_BASE_URL=http://preview.corp.test、SESSION_COOKIE_SECURE=false

# 启动全部服务
docker compose -f infra/compose/docker-compose.yml up -d --build

# 冒烟验证
./scripts/smoke-test.sh
```

## 3. 首次初始化

1. 访问 `http://prototype.corp.test/login`，使用 `BOOTSTRAP_ADMIN_USERNAME/PASSWORD` 登录（首次登录强制修改密码）；
2. 在「系统管理 → 分类与标签」创建分类；
3. 在「系统管理 → 用户管理」创建创建者/查看者账号；
4. 在「系统管理 → 系统配置」按需调整上传限制、回收站保留天数、验证码开关等。

## 4. 验证清单

- `./scripts/smoke-test.sh` 全部 PASS；
- `E2E_SERVER_IP=<服务器IP> E2E_ADMIN_USERNAME=... E2E_ADMIN_PASSWORD=... ./scripts/verify-http-isolation.sh` 全部 PASS；
- `E2E_SERVER_IP=<服务器IP> E2E_ADMIN_USERNAME=... E2E_ADMIN_PASSWORD=... pnpm --filter @prototype/web test:e2e` 使用 Chromium 映射两个域名并全部 PASS；
- 浏览器分别访问两个域名：管理端登录、上传发布、分享页预览正常；
- 预览域访问 `/api/...` 返回 404；
- Prometheus：`curl http://127.0.0.1:9090/-/ready` 返回 Ready；`http://127.0.0.1:9090/targets` 显示 api 与 preview-gateway 两个 UP。

## 5. 运维要点

- **纯 HTTP 仅限受控内网测试**：不暴露到公网；测试环境不要使用与其他系统相同的密码；
- 备份：按 `docs/runbooks/backup-and-restore.md` 配置每日备份；
- 日志：容器 `docker compose logs -f api` 输出 JSON 结构化日志，含 traceId，可按追踪 ID 定位；
- 发布任务失败：管理端版本记录查看校验报告；日志中 `PublishJobRunner` 输出失败阶段；
- 磁盘/容量：MinIO 与 MySQL 数据在命名卷中，注意磁盘水位告警（80%）。

## 6. 常见问题

| 现象 | 处理 |
| --- | --- |
| 测试机无法访问两个域名 | 检查 `/etc/hosts` 与服务器防火墙 80 端口 |
| 上传报签名错误 | 确认 `UPLOAD_PUBLIC_BASE_URL` 与访问域名一致（`http://prototype.corp.test/upload-objects`），不要用 IP:端口 |
| 预览 iframe 空白 | 确认 `preview.corp.test` 可解析；管理端 iframe 使用预览域绝对地址 |
| 验证码登录失败 | 系统配置 `captcha.enabled` 为 false 时无需验证码；开启后刷新验证码重试 |
| Cookie 隔离脚本无法连接 | 设置 `E2E_SERVER_IP`，脚本会用 `curl --resolve` 保留两个主机名并连接同一服务器 |
