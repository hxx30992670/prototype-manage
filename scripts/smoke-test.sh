#!/usr/bin/env bash
# 原型管理平台冒烟测试
# 前置：docker compose up -d --build 已启动；/etc/hosts 配置两个域名。
# 用法: ./scripts/smoke-test.sh
set -euo pipefail

MANAGEMENT_HOST="${MANAGEMENT_HOST:-prototype.corp.test}"
PREVIEW_HOST="${PREVIEW_HOST:-preview.corp.test}"
BASE="${BASE:-http://127.0.0.1}"
FAILED=0

check() {
  local desc="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo "  [PASS] $desc"
  else
    echo "  [FAIL] $desc (期望 $expected, 实际 $actual)" >&2
    FAILED=1
  fi
}

echo "[smoke] 1. 管理域 CSRF 接口"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $MANAGEMENT_HOST" "$BASE/api/v1/auth/csrf")
check "管理域 /api/v1/auth/csrf" "200" "$code"

echo "[smoke] 2. 预览域健康检查"
body=$(curl -s -H "Host: $PREVIEW_HOST" "$BASE/health")
check "预览域 /health" "preview-ok" "$body"

echo "[smoke] 3. 预览域拒绝管理 API"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $PREVIEW_HOST" "$BASE/api/v1/auth/me")
check "预览域 /api/v1/auth/me" "404" "$code"

echo "[smoke] 4. 管理域拒绝内容通道"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $MANAGEMENT_HOST" "$BASE/content/c/fake/index.html")
check "管理域 /content/ 返回 404" "404" "$code"

echo "[smoke] 5. 管理域静态页与安全头"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $MANAGEMENT_HOST" "$BASE/")
check "管理域首页" "200" "$code"
csp=$(curl -sI -H "Host: $MANAGEMENT_HOST" "$BASE/" | tr -d '\r' | grep -i '^content-security-policy:' | head -1)
if [[ "$csp" == *"default-src 'self'"* ]]; then
  echo "  [PASS] 管理域 CSP 存在"
else
  echo "  [FAIL] 管理域 CSP 缺失: $csp" >&2
  FAILED=1
fi

echo "[smoke] 6. 管理域上传受限"
code=$(curl -s -o /dev/null -w '%{http_code}' -X GET -H "Host: $MANAGEMENT_HOST" "$BASE/upload-objects/prototype-objects/")
check "上传路径拒绝 GET" "403" "$code"

echo "[smoke] 7. 预览域不开放上传和 MinIO Console"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $PREVIEW_HOST" "$BASE/upload-objects/prototype-objects/")
check "预览域上传路径" "404" "$code"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $MANAGEMENT_HOST" "$BASE/minio/console/")
check "MinIO Console 路径" "404" "$code"

echo "[smoke] 8. API 健康检查（通过同网络容器）"
body=$(docker compose -f infra/compose/docker-compose.yml exec -T nginx \
  sh -c 'wget -q -T 5 -O - http://api:8081/actuator/health 2>/dev/null' || echo '{"status":"DOWN"}')
if [[ "$body" == *'"UP"'* ]]; then
  echo "  [PASS] API actuator health UP"
else
  echo "  [FAIL] API actuator health: $body" >&2
  FAILED=1
fi

echo "[smoke] 9. Prometheus ready"
prometheus_body=$(curl -sS -L --max-time 5 http://127.0.0.1:9090/-/ready 2>/dev/null || true)
prometheus_body="${prometheus_body%$'\n'}"
if [[ "$prometheus_body" == "Prometheus is Ready." || "$prometheus_body" == "Prometheus Server is Ready." ]]; then
  echo "  [PASS] Prometheus /-/ready"
else
  echo "  [FAIL] Prometheus /-/ready (实际 $prometheus_body)" >&2
  FAILED=1
fi

if [[ "$FAILED" == "1" ]]; then
  echo "[smoke] 结果: FAIL" >&2
  exit 1
fi
echo "[smoke] 结果: ALL PASS"
