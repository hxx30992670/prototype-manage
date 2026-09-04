#!/usr/bin/env bash
# 原型管理平台恢复脚本（演练/灾难恢复）
# 恢复顺序固定为：停写 → 恢复 MySQL 到临时库 → 恢复 MinIO 临时前缀 → 跑一致性扫描 → 切换正式数据
# 用法:
#   ./scripts/restore.sh --target /tmp/restore-work \
#     --confirm-backup-id 20260901T120000Z \
#     --mysql-database prototype_db_restore
# 参数:
#   --target              恢复工作目录（必须为空或不存在，禁止直接写生产路径）
#   --confirm-backup-id   确认要恢复的备份 ID（如 20260901T120000Z），防止误恢复
#   --mysql-database      恢复到哪个临时数据库名（默认 prototype_db_restore）
# 依赖: mysql、mysqldump、mc、gunzip
set -euo pipefail

TARGET=""
CONFIRM_BACKUP_ID=""
RESTORE_DB="${RESTORE_DB:-prototype_db_restore}"
BACKUP_DIR="${BACKUP_DIR:?必须设置 BACKUP_DIR（备份所在目录）}"
MYSQL_HOST="${MYSQL_HOST:?必须设置 MYSQL_HOST}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:?必须设置 MYSQL_USER}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?必须设置 MYSQL_PASSWORD（不回显）}"
MYSQL_ADMIN_DB="${MYSQL_ADMIN_DB:-mysql}"
MINIO_BUCKET="${MINIO_BUCKET:-prototype-objects}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target) TARGET="${2:?--target 需要值}"; shift 2 ;;
    --confirm-backup-id) CONFIRM_BACKUP_ID="${2:?--confirm-backup-id 需要值}"; shift 2 ;;
    --mysql-database) RESTORE_DB="$2"; shift 2 ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$TARGET" || -z "$CONFIRM_BACKUP_ID" ]]; then
  echo "错误：必须提供 --target 和 --confirm-backup-id，禁止无参数覆盖当前数据" >&2
  exit 1
fi

mysql_backup="$BACKUP_DIR/mysql-$CONFIRM_BACKUP_ID.sql.gz"
minio_backup="$BACKUP_DIR/minio/$CONFIRM_BACKUP_ID/prototype-objects"
if [[ ! -f "$mysql_backup" || ! -d "$minio_backup" ]]; then
  echo "错误：备份不存在（$CONFIRM_BACKUP_ID），请检查 BACKUP_DIR" >&2
  exit 1
fi

# 校验和
sha256sum -c "$mysql_backup.sha256" >/dev/null 2>&1 || {
  echo "错误：备份校验和失败，停止恢复" >&2
  exit 1
}

if [[ -d "$TARGET" && -n "$(ls -A "$TARGET" 2>/dev/null)" ]]; then
  echo "错误：--target 目录非空，拒绝恢复" >&2
  exit 1
fi
mkdir -p "$TARGET"

echo "[restore] 开始恢复备份 $CONFIRM_BACKUP_ID -> $RESTORE_DB"

# 1. 停写：应用侧暂停（手册中人工执行；脚本仅输出提示）
echo "[restore] 提示：请先在应用侧暂停写入（停 API/Worker 或切换只读），确认后继续（Ctrl-C 中止）"
read -r -p "确认继续恢复？[yes/N] " confirm
if [[ "$confirm" != "yes" ]]; then
  echo "已取消恢复" >&2
  exit 1
fi

# 2. 恢复 MySQL 到临时库
mysql \
  --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS \`$RESTORE_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
gunzip -c "$mysql_backup" | mysql \
  --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" --password="$MYSQL_PASSWORD" \
  "$RESTORE_DB"

# 3. 恢复 MinIO 到临时前缀（mc mirror 到 minio/${MINIO_BUCKET}/restore-test/<backup_id>/）
restore_prefix="restore-test/$CONFIRM_BACKUP_ID"
mc mirror --overwrite "$minio_backup" "minio/${MINIO_BUCKET}/${restore_prefix}"
echo "[restore] MinIO 已恢复到临时前缀 $restore_prefix"

# 4. 一致性扫描：恢复后的数据应运行平台一致性扫描确认对象与记录对齐
echo "[restore] 提示：请对 $RESTORE_DB 运行一致性扫描（API 每日任务或手工触发），确认无缺失对象后再切换正式数据"

# 5. 切换正式数据（人工确认后执行）
echo "[restore] 完成：临时库 $RESTORE_DB 与 MinIO 前缀 $restore_prefix 已就绪。"
echo "[restore] 切换步骤（人工执行）: 停写 → 用临时库替换正式库（或改 SPRING_DATASOURCE_URL）→ mc mirror 恢复前缀到正式前缀 → 恢复写流量"
