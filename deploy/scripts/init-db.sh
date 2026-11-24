#!/usr/bin/env bash
# ============================================================
# 初始化数据库：执行完整建表脚本（MySQL 8.0+）
# 用法：sudo ./init-db.sh [schema.sql 路径]
# 依赖：mysql 客户端；执行前请确认 my.cnf 已配置 default-time-zone='+08:00'
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA="${1:-${SCRIPT_DIR}/../../docs/sql/schema.sql}"

if [[ ! -f "$SCHEMA" ]]; then
  echo "找不到建表脚本: $SCHEMA" >&2
  exit 1
fi

echo "即将执行建表脚本: $SCHEMA"
read -r -p "MySQL root 用户名 [root]: " DBROOT
DBROOT="${DBROOT:-root}"
mysql -u "$DBROOT" -p < "$SCHEMA"
echo "建表完成。请确认已创建最小权限账号 cockpit@127.0.0.1。"
