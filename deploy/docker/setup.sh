#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
DOMAIN="${1:-}"
if [[ ! "$DOMAIN" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || [[ "$DOMAIN" != *.* ]]; then
  echo '用法: bash deploy/docker/setup.sh study.example.com（只填已解析到服务器的域名，不含协议或路径）' >&2
  exit 1
fi
if [[ -e .env ]]; then
  echo '.env 已存在，保留原配置；请自行编辑，避免改变数据库密码导致已有数据库无法连接。' >&2
  exit 1
fi
command -v openssl >/dev/null || { echo '请先安装 openssl' >&2; exit 1; }
umask 077
DB_PASSWORD="$(openssl rand -hex 32)"
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 32)"
JWT_SECRET="$(openssl rand -hex 32)"
FILE_TOKEN_SECRET="$(openssl rand -hex 32)"
set -C
cat > .env <<EOF
APP_DOMAIN=$DOMAIN
DB_PASSWORD=$DB_PASSWORD
MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD
JWT_SECRET=$JWT_SECRET
FILE_TOKEN_SECRET=$FILE_TOKEN_SECRET
APP_ALLOW_REGISTER=true
EOF
echo '已生成 .env，包含独立随机密码与密钥，权限为 600。'
echo '下一步: docker compose up -d --build --wait'
