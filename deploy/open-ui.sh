#!/usr/bin/env bash
# ============================================================================
# open-ui.sh —— 在本机(Mac/Linux)一键打开 TiDB demo 管理页面
# 原理：SSH 本地端口转发 -L，把服务器 127.0.0.1:18080 映射到本机 127.0.0.1:18080
#       服务器公网不开放任何新端口，页面只在回环地址上可达
# 前置：~/.ssh/config 中已配置 Host tidb160（端口 50017 / 密钥 ~/.ssh/id_tidb160）
# ============================================================================
set -u
HOST="${TIDB_SSH_HOST:-tidb160}"
LOCAL_PORT="${LOCAL_PORT:-18080}"
REMOTE_PORT="${REMOTE_PORT:-18080}"

if lsof -nP -iTCP:"${LOCAL_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "隧道已在监听本机 ${LOCAL_PORT}"
else
  echo "建立隧道: localhost:${LOCAL_PORT} -> ${HOST}:127.0.0.1:${REMOTE_PORT}"
  ssh -f -N -o ExitOnForwardFailure=yes -L "${LOCAL_PORT}:127.0.0.1:${REMOTE_PORT}" "${HOST}" || {
    echo "隧道建立失败：请确认 ssh ${HOST} 可免密登录，且服务器 sshd 已开启 AllowTcpForwarding local"
    exit 1
  }
  sleep 1
fi

URL="http://127.0.0.1:${LOCAL_PORT}/"
echo "打开 ${URL}"
command -v open >/dev/null 2>&1 && open "$URL" || echo "请手动在浏览器打开: $URL"
