# HTTPS 切换手册（HTTP → HTTPS）

业务代码无需修改：仅更换环境变量与 Nginx 配置。切换前必须在测试环境完成一次演练。

## 1. 切换前检查

- 两个域名的证书就绪（含泛域名或双证书）；
- 确认无业务依赖 HTTP 明文地址（如预签名 URL 硬编码 http）；
- 备份当前 Nginx 配置与 `.env`。

## 2. 变更清单

### 2.1 环境变量（`.env`）

```dotenv
APP_SCHEME=https
SESSION_COOKIE_SECURE=true
# 域名不变，仅协议变化（如前端有绝对地址拼接，全部以 BASE URL 环境变量为准）
PREVIEW_BASE_URL=https://preview.corp.test
```

### 2.2 Nginx

1. 使用 `infra/nginx/conf.d/https.conf.template` 生成正式配置：
   - 替换 `${PROTOTYPE_DOMAIN}` / `${PREVIEW_DOMAIN}`；
   - 若使用 `envsubst`，只替换域名变量：`envsubst '${PROTOTYPE_DOMAIN} ${PREVIEW_DOMAIN}' < https.conf.template > default.conf`，避免误替换 Nginx 的 `$host` 等变量；
   - 挂载证书到 `/etc/nginx/certs/`；
   - 默认启用 HSTS `max-age=31536000`（不含 includeSubDomains）；
   - **确认所有子域均支持 HTTPS 且无 HTTP 依赖后**，再启用 `includeSubDomains`（此操作会强制所有子域 HTTPS，回退成本高）；
2. HTTP 80 全部 301 跳转 HTTPS；
3. CSP 中的 `frame-src` 改为 `https://preview.corp.test`（模板已处理）。

### 2.3 应用

- 重新 `docker compose up -d --build`；
- 应用启动后 Cookie 自动带 `Secure`（`SESSION_COOKIE_SECURE=true`）。

## 3. 演练步骤

```bash
# 1. 生成自签证书（仅演练用）
mkdir -p certs && openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout certs/prototype.corp.test.key -out certs/prototype.corp.test.pem \
  -days 30 -subj "/CN=prototype.corp.test"
# 为 preview.corp.test 重复一次

# 2. 用模板生成配置并挂载（演练环境替换 http.conf）；Compose API 同步设置 SESSION_COOKIE_SECURE=true 和 PREVIEW_BASE_URL=https://preview.corp.test
# 3. 重启 nginx，验证：
curl -fsSI https://prototype.corp.test/ | grep -i strict-transport-security
curl -fsSI https://preview.corp.test/health
# 4. 验证 Cookie：
#    - 登录后 Cookie 带 Secure
#    - 分享页 /s/{token} 正常加载，原型 iframe 走 https
# 5. 验证 HTTP 跳转：
curl -sI http://prototype.corp.test/ | head -1   # 301
```

## 4. 回滚

- 恢复 `.env`（`SESSION_COOKIE_SECURE=false`）与 `http.conf`；
- 重新构建重启；
- 回滚后浏览器需清除旧 HTTPS Cookie（Domain 相同、Secure 属性变化可能导致登录异常）。

## 5. 验收点

- [ ] HTTP 全部 301 到 HTTPS；
- [ ] 两个域名 TLS 握手成功，证书链完整；
- [ ] 管理 Cookie 与分享 Cookie 均带 `Secure`、`HttpOnly`、`Path=/`；
- [ ] HSTS 生效；`includeSubDomains` 仅在确认全部子域支持 HTTPS 后启用；
- [ ] 上传直传（`UPLOAD_PUBLIC_BASE_URL` 为 https）签名可用；
- [ ] 原型 iframe 在 HTTPS 页面正常加载（CSP `frame-src` 与 Gateway CSP 均允许 https 预览域）。
