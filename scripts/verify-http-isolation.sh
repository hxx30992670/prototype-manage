#!/usr/bin/env bash
# 管理 Cookie 与预览域隔离验证
# 前置：Compose 已启动；两个主机名解析到测试服务器；账号已可登录。
# 本机直连 Docker 时可加 E2E_SERVER_IP=127.0.0.1，脚本会用 --resolve 保留真实主机名。
# 用法: E2E_SERVER_IP=127.0.0.1 E2E_ADMIN_USERNAME=admin \
#   E2E_ADMIN_PASSWORD='xxx' ./scripts/verify-http-isolation.sh
set -euo pipefail

: "${E2E_ADMIN_USERNAME:?必须设置 E2E_ADMIN_USERNAME}"
: "${E2E_ADMIN_PASSWORD:?必须设置 E2E_ADMIN_PASSWORD}"

MANAGEMENT_HOST="${MANAGEMENT_HOST:-prototype.corp.test}"
PREVIEW_HOST="${PREVIEW_HOST:-preview.corp.test}"
MANAGEMENT_URL="${MANAGEMENT_URL:-http://${MANAGEMENT_HOST}}"
PREVIEW_URL="${PREVIEW_URL:-http://${PREVIEW_HOST}}"
E2E_SERVER_IP="${E2E_SERVER_IP:-}"
FAILED=0

resolve_args=()
if [[ -n "$E2E_SERVER_IP" ]]; then
  resolve_args=(--resolve "${MANAGEMENT_HOST}:80:${E2E_SERVER_IP}" --resolve "${PREVIEW_HOST}:80:${E2E_SERVER_IP}")
fi

curl_http() {
  curl -sS "${resolve_args[@]}" "$@"
}

cookie_jar="$(mktemp)"
login_headers="$(mktemp)"
preview_trace="$(mktemp)"
trap 'rm -f "$cookie_jar" "$login_headers" "$preview_trace"' EXIT

echo "[isolation] 1. 获取 CSRF Token 并登录管理域"
csrf_token="$(curl_http -c "$cookie_jar" "$MANAGEMENT_URL/api/v1/auth/csrf" | jq -r '.data.token')"
if [[ -z "$csrf_token" || "$csrf_token" == "null" ]]; then
  echo "[isolation] 无法获取 CSRF Token" >&2
  exit 1
fi

login_json="$(jq -n --arg username "$E2E_ADMIN_USERNAME" --arg password "$E2E_ADMIN_PASSWORD" \
  '{username:$username,password:$password}')"

curl_http -D "$login_headers" -o /dev/null -b "$cookie_jar" -c "$cookie_jar" \
  -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $csrf_token" -X POST \
  --data "$login_json" "$MANAGEMENT_URL/api/v1/auth/login"

admin_cookie="$(grep -i '^Set-Cookie: PH_ADMIN_SESSION=' "$login_headers" | head -1 | sed 's/^[^:]*: //')"

echo "[isolation] 2. 校验管理 Cookie 属性"
case "$admin_cookie" in
  *"PH_ADMIN_SESSION="*"Path=/"*"HttpOnly"*) echo "  [PASS] PH_ADMIN_SESSION + Path=/ + HttpOnly" ;;
  *) echo "  [FAIL] 管理 Cookie 属性错误: $admin_cookie" >&2; FAILED=1 ;;
esac
if printf '%s' "$admin_cookie" | grep -Eq 'Domain=|Secure'; then
  echo "  [FAIL] HTTP 测试环境 Cookie 不应包含 Domain 或 Secure" >&2
  FAILED=1
else
  echo "  [PASS] 无 Domain、无 Secure（HTTP 环境）"
fi

echo "[isolation] 3. 登录态可用（管理域携带 Cookie 访问 /me）"
me_code="$(curl -s -o /dev/null -w '%{http_code}' -b "$cookie_jar" \
  "${resolve_args[@]}" "$MANAGEMENT_URL/api/v1/auth/me")"
if [[ "$me_code" == "200" ]]; then
  echo "  [PASS] 管理域 /me 返回 200"
else
  echo "  [FAIL] 管理域 /me 返回 $me_code" >&2
  FAILED=1
fi

echo "[isolation] 4. 预览域请求不携带管理 Cookie（Host-only 隔离）"
# cookie jar 按域隔离：预览域请求不会带上管理域的 PH_ADMIN_SESSION。
# 用 curl 的请求头追踪验证预览域请求中没有 Cookie 头。
curl -sv -b "$cookie_jar" "${resolve_args[@]}" "$PREVIEW_URL/health" \
  -o /dev/null 2>"$preview_trace"
if grep -qi '^> Cookie:' "$preview_trace"; then
  echo "  [FAIL] 预览域请求携带了 Cookie，Host-only 隔离失效" >&2
  FAILED=1
else
  echo "  [PASS] 预览域请求无 Cookie 头"
fi

echo "[isolation] 5. 分享 API 不接受管理会话（无票据/会话时拒绝）"
share_code="$(curl -s -o /dev/null -w '%{http_code}' -b "$cookie_jar" \
  "${resolve_args[@]}" "$PREVIEW_URL/share-api/v1/shares/doesnotexist/bootstrap")"
if [[ "$share_code" == "404" || "$share_code" == "403" ]]; then
  echo "  [PASS] 分享 API 对无效 Token 返回 $share_code"
else
  echo "  [FAIL] 分享 API 返回 $share_code" >&2
  FAILED=1
fi

if [[ "$FAILED" == "1" ]]; then
  echo "[isolation] 结果: FAIL" >&2
  exit 1
fi
echo "[isolation] 结果: ALL PASS"
