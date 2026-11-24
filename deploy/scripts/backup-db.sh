#!/usr/bin/env bash
# ============================================================================
# 数据库定时备份脚本（个人单机部署）
# 用法：由 cron 每日调用；数据库账号/密码建议从只读权限的备份账号读取。
# 保留最近 BACKUP_KEEP 天的备份，自动清理过期压缩包。
#
# crontab 示例（每天 02:30 执行）：
#   30 2 * * * /opt/study-cockpit/deploy/scripts/backup-db.sh >> /var/log/study-cockpit/backup.log 2>&1
# ============================================================================
set -euo pipefail
umask 077

# ---- 可调参数（可被同名环境变量覆盖） ----
DB_NAME="${DB_NAME:-study_cockpit}"
DB_USER="${DB_USER:-cockpit_backup}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
BACKUP_DIR="${BACKUP_DIR:-/backup/study-cockpit}"
BACKUP_KEEP="${BACKUP_KEEP:-14}"
TIMESTAMP="$(date +%F_%H%M%S)"
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}_$$.sql.gz"

[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'DB_NAME 仅允许字母、数字和下划线' >&2; exit 1; }
[[ "$BACKUP_KEEP" =~ ^[0-9]+$ ]] || { echo 'BACKUP_KEEP 必须是非负整数' >&2; exit 1; }
[[ "$DB_PORT" =~ ^[0-9]+$ ]] || { echo 'DB_PORT 必须是端口数字' >&2; exit 1; }
: "${DB_PASSWORD:?请设置 DB_PASSWORD 环境变量}"

mkdir -p "${BACKUP_DIR}"
chmod 750 "${BACKUP_DIR}"

# 密码通过 MYSQL_PWD 环境变量传入，避免出现在命令行进程列表中
export MYSQL_PWD="${DB_PASSWORD:?请设置 DB_PASSWORD 环境变量}"
TEMP_FILE="$(mktemp "${BACKUP_DIR}/.backup-XXXXXX")"
trap 'rm -f -- "$TEMP_FILE"' EXIT

echo "[${TIMESTAMP}] 开始备份 ${DB_NAME} -> ${BACKUP_FILE}"
mysqldump --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF \
  -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" "${DB_NAME}" \
  | gzip -9 > "${TEMP_FILE}"

gzip -t "${TEMP_FILE}"
chmod 640 "${TEMP_FILE}"
mv -- "${TEMP_FILE}" "${BACKUP_FILE}"

# 清理超过保留天数的旧备份
find "${BACKUP_DIR}" -maxdepth 1 -type f -name "${DB_NAME}_*.sql.gz" -mtime "+${BACKUP_KEEP}" -delete

echo "[${TIMESTAMP}] 备份完成，当前保留 $(find "${BACKUP_DIR}" -name '*.sql.gz' | wc -l) 份备份"
