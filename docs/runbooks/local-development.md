# 本地开发手册

本手册用于本机开发和测试。生产服务器继续使用 `infra/compose/docker-compose.yml`，不要将本地开发 Compose 用于部署。

## 前置条件

- JDK 21：本机 Homebrew 路径为 `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`。
- Node 24、pnpm 10、Docker Desktop。
- 18000、5173、18080、18081、18082、18083、13306、16379、19000、19001 端口未被目标服务占用。

本项目本地后端固定使用 18080 至 18083，可与占用 8080 的其他项目同时运行；不要用本项目脚本杀死其他项目进程。

## 首次配置

1. 创建仅本机使用的环境文件：

   ```bash
   cp -n .env.dev.example .env.dev
   ```

   根据需要修改 `.env.dev` 中的本地密码；该文件已被 Git 忽略。

2. 启动 Docker 基础设施与反向代理：

   ```bash
   docker compose --env-file .env.dev -f infra/compose/docker-compose.dev.yml up -d
   docker compose --env-file .env.dev -f infra/compose/docker-compose.dev.yml ps
   ```

3. 在 IDEA 打开 `services/backend/pom.xml`，将 Project SDK 和 Maven Runner JRE 都设置为 JDK 21。创建 Application Run Configuration，主类为：

   ```text
   com.company.prototype.localdev.LocalDevApplication
   ```

   `LocalDevApplication` 会从 IDEA 的工作目录向上自动找到并加载项目根目录的 `.env.dev`；无需在 IDEA 中逐项添加数据库、Redis、MinIO 或域名变量。

   如果之前已经手工添加过环境变量，请将它们从该运行配置中删除。IDEA 显式环境变量优先级更高，会覆盖 `.env.dev` 的本地配置。仅在临时调试时才保留同名变量作为覆盖值。

   不要在生产服务中使用该本地加载器。生产容器继续由 Compose、Kubernetes、systemd 或部署平台注入环境变量与密钥。

4. 在 VS Code 打开仓库根目录并运行：

   ```bash
   corepack enable
   pnpm install
   pnpm --filter @prototype/web dev
   ```

浏览器访问 `http://prototype.localhost:18000`，不要直接用 `127.0.0.1:5173` 或 `prototype.localhost:5173`，这样才能保持上传签名和双域 Cookie 行为一致。`*.localhost` 由浏览器原生解析到本机，无需修改 `/etc/hosts`。

## 日常验证

```bash
curl -fsS -H 'Host: prototype.localhost:18000' http://127.0.0.1:18000/api/v1/auth/csrf
curl -fsS -H 'Host: preview.localhost:18000' http://127.0.0.1:18000/health
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: preview.localhost:18000' http://127.0.0.1:18000/api/v1/auth/me
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: prototype.localhost:18000' http://127.0.0.1:18000/content/c/invalid/index.html
```

期望依次为 CSRF JSON、`preview-ok`、`404`、`404`。

IDEA 一个进程会启动 API（18080）、API Actuator（18081）、发布 Worker、预览网关（18082）和 Gateway Actuator（18083）。停止 IDEA 的 `LocalDevApplication` 即可一起停止后端三个 Context。

## 停止与清理

停止本地基础设施但保留开发数据：

```bash
docker compose --env-file .env.dev -f infra/compose/docker-compose.dev.yml down
```

如需从零开始，仅删除明确的开发卷：

```bash
docker compose --env-file .env.dev -f infra/compose/docker-compose.dev.yml down -v
```

这不会删除生产或测试服务器的 Docker 资源。
