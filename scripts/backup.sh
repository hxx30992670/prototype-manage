#!/usr/bin/env bash
# 原型管理平台每日备份脚本
# 用法: BACKUP_DIR=/data/backups MYSQL_HOST=mysql MYSQL_USER=prototype_user \
#       MYSQL_PASSWORD=xxx MYSQL_DATABASE=prototype_db MINIO_BUCKET=prototype-objects \
#       ./scripts/backup.sh
# 依赖: mysqldump、mc (MinIO Client)、gzip、sha256sum
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:?必须设置 BACKUP_DIR（备份输出目录）}"
MYSQL_HOST="${MYSQL_HOST:?必须设置 MYSQL_HOST}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:?必须设置 MYSQL_USER}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?必须设置 MYSQL_PASSWORD（不回显）}"
MYSQL_DATABASE="${MYSQL_DATABASE:?必须设置 MYSQL_DATABASE}"
MINIO_BUCKET="${MINIO_BUCKET:-prototype-objects}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
BACKUP_SAFE_PREFIX="${BACKUP_SAFE_PREFIX:-/data/backups}"

# 安全校验：删除保留前必须确认备份目录是明确配置的路径
case "$BACKUP_DIR" in
  "$BACKUP_SAFE_PREFIX"*|"$PWD"*) ;;
  *) echo "拒绝操作：BACKUP_DIR 不在允许的安全前缀内" >&2; exit 1 ;;
esac
mkdir -p "$BACKUP_DIR/minio"

backup_id="$(date -u +%Y%m%dT%H%M%SZ)"
mysql_file="$BACKUP_DIR/mysql-$backup_id.sql.gz"

echo "[backup] $backup_id: 开始备份"

# 1. MySQL 全量备份（单事务一致性快照，含存储过程与触发器）
mysqldump \
  --single-transaction \
  --routines \
  --triggers \
  --host="$MYSQL_HOST" \
  --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" \
  --password="$MYSQL_PASSWORD" \
  "$MYSQL_DATABASE" | gzip > "$mysql_file"

# 2. MinIO 单桶镜像备份（覆盖源文件、发布文件、附件与封面）
mc mirror --overwrite "minio/${MINIO_BUCKET}" "$BACKUP_DIR/minio/$backup_id/prototype-objects"

# 3. 校验和
sha256sum "$mysql_file" > "$mysql_file.sha256"

echo "[backup] $backup_id: 完成（MySQL + MinIO）"

# 4. 保留策略：仅删除明确配置目录下超过保留期的备份
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'mysql-*.sql.gz' -mtime +"$RETENTION_DAYS" -print -delete
find "$BACKUP_DIR/minio" -maxdepth 1 -type d -name '[0-9]*T*' -mtime +"$RETENTION_DAYS" -print -exec rm -rf {} +
