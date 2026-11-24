#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
umask 077
mkdir -p backups
STAMP="$(date +%F_%H%M%S)_$$"
TEMP_FILE="$(mktemp "backups/.backup-XXXXXX")"
trap 'rm -f -- "$TEMP_FILE"' EXIT
docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF -u cockpit study_cockpit' | gzip -9 > "$TEMP_FILE"
gzip -t "$TEMP_FILE"
mv -- "$TEMP_FILE" "backups/study_cockpit_${STAMP}.sql.gz"
echo "已备份数据库: backups/study_cockpit_${STAMP}.sql.gz"
echo '该文件不含上传图片。请将备份复制到另一台设备或备份存储。'
