#!/usr/bin/env bash
# ============================================================
# 生成初始化账号的 SQL（BCrypt 加盐密文，绝不明文）
# 用法：./init-user.sh <username>
# 密文由 htpasswd 生成（$2y$ 前缀可被 Spring Security BCrypt 识别）
# 脚本只输出 SQL，不直接写库，便于人工核对后再执行
# 推荐替代方案：临时开启 APP_ALLOW_REGISTER 走注册接口，见部署文档 §8
# ============================================================
set -euo pipefail

USERNAME="${1:-}"
if [[ -z "$USERNAME" ]]; then
  echo "用法: $0 <username>" >&2
  exit 1
fi
if [[ ! "$USERNAME" =~ ^[A-Za-z0-9_.-]{3,64}$ ]]; then
  echo "用户名需为 3~64 位字母/数字/_/./-" >&2
  exit 1
fi
if ! command -v htpasswd >/dev/null 2>&1; then
  echo "缺少 htpasswd，请先安装 apache2-utils" >&2
  exit 1
fi

# 交互式读取密码，避免明文出现在 shell history
read -r -s -p "输入密码(至少 8 位): " PASSWORD
echo
read -r -s -p "再次输入密码: " PASSWORD2
echo
if [[ "$PASSWORD" != "$PASSWORD2" ]]; then
  echo "两次密码不一致" >&2
  exit 1
fi
if [[ ${#PASSWORD} -lt 8 ]]; then
  echo "密码至少 8 位" >&2
  exit 1
fi

# -B 使用 bcrypt，-C 12 与后端 BCryptPasswordEncoder(12) 强度一致
HASH="$(htpasswd -bnBC 12 "" "$PASSWORD" | tr -d ':\n')"

echo "-- 请人工核对后执行以下 SQL："
echo "INSERT INTO sys_user (username, password, nickname, status, token_version)"
echo "VALUES ('${USERNAME}', '${HASH}', '${USERNAME}', 1, 0);"

unset PASSWORD PASSWORD2
