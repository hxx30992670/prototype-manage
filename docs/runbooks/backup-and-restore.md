# 备份与恢复手册

本文档描述原型管理平台的每日备份、恢复演练与灾难恢复流程。所有命令在生产操作前必须先在测试环境演练通过。

## 1. 备份内容

| 数据 | 存储位置 | 备份方式 |
| --- | --- | --- |
| 业务数据（原型、版本、评论、分享、审计等） | MySQL `prototype_db` | `mysqldump --single-transaction` 每日全量 |
| 对象文件（源文件、发布文件、附件、封面） | MinIO 单桶 `prototype-objects` | `mc mirror` 每日镜像 |
| 系统配置、Nginx 配置、Compose 文件 | Git 仓库 | 版本控制（无需额外备份） |

## 2. 每日备份

### 2.1 手动执行

```bash
export BACKUP_DIR=/data/backups
export MYSQL_HOST=mysql
export MYSQL_USER=prototype_user
export MYSQL_PASSWORD='<密码>'
export MYSQL_DATABASE=prototype_db
export MINIO_BUCKET=prototype-objects
./scripts/backup.sh
```

脚本行为：

- 生成备份 ID：`YYYYMMDDTHHMMSSZ`（UTC）；
- MySQL 备份：`BACKUP_DIR/mysql-<id>.sql.gz` + SHA-256 校验和；
- MinIO 备份：`BACKUP_DIR/minio/<id>/prototype-objects/` 完整镜像；
- 保留策略：默认 30 天，超过保留期的备份自动删除；删除前校验 `BACKUP_DIR` 位于 `BACKUP_SAFE_PREFIX`（默认 `/data/backups`）或当前工作目录下，防止误删。

### 2.2 定时任务（cron 示例）

```cron
# 每天 02:00 执行备份（配置环境变量后）
0 2 * * * /usr/bin/env BACKUP_DIR=/data/backups MYSQL_HOST=mysql MYSQL_USER=prototype_user MYSQL_PASSWORD='<密码>' MYSQL_DATABASE=prototype_db /path/to/prototypeManage/scripts/backup.sh >> /var/log/prototype-backup.log 2>&1
```

> 真实环境请通过 systemd timer、crontab 或运维平台的密钥管理注入密码，不要在命令行明文回显。

## 3. 恢复流程

### 3.1 恢复原则

- **永远先恢复到临时位置**，验证通过后再切换正式数据；
- 恢复顺序固定：停写 → 恢复 MySQL 到临时库 → 恢复 MinIO 临时前缀 → 一致性扫描 → 切换正式数据；
- 禁止在未确认备份 ID 的情况下执行任何覆盖操作。

### 3.2 执行恢复演练

```bash
export BACKUP_DIR=/data/backups
export MYSQL_HOST=mysql
export MYSQL_USER=root
export MYSQL_PASSWORD='<密码>'
./scripts/restore.sh \
  --target /tmp/restore-work \
  --confirm-backup-id 20260901T020000Z \
  --mysql-database prototype_db_restore
```

脚本执行：

1. 校验备份文件与 SHA-256 校验和；
2. 确认 `--target` 目录为空；
3. 提示并人工确认（需输入 `yes`）；
4. 创建临时库 `prototype_db_restore` 并导入；
5. `mc mirror` 恢复 MinIO 到临时前缀 `restore-test/<backup_id>/`；
6. 输出切换提示。

### 3.3 切换正式数据（人工步骤）

1. 停写：停止 API/Worker 服务（或切换只读配置）；
2. 切换数据库：将临时库重命名/替换为正式库，或修改 `SPRING_DATASOURCE_URL` 指向临时库；
3. 切换对象：`mc mirror --overwrite "minio/prototype-objects/restore-test/<backup_id>/" "minio/prototype-objects"`；
4. 启动服务，运行一致性扫描确认无缺失对象；
5. 恢复写流量，观察监控与日志。

## 4. 恢复演练要求

- 每季度至少执行一次完整恢复演练（设计文档 §20.3）；
- 演练必须验证：数据库记录与对象文件可同时恢复、版本与当前版本指针一致、分享/票据索引（Redis）随会话失效机制重建、备份保留策略生效；
- 演练结果记录在验收报告中。

## 5. 备份监控与告警

- 备份失败告警：`prototype_db` 备份任务失败或校验和不一致时告警（见 `infra/monitoring/alert-rules.yml` 及运维侧 cron 监控）；
- 磁盘告警：备份目录所在磁盘使用率超过 80% 触发告警；
- 每日检查 `mysql-<id>.sql.gz.sha256` 存在且校验通过。
