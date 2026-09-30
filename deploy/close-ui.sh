#!/usr/bin/env bash
# 关闭本机到服务器的 SSH 端口转发隧道（不影响服务器上的服务，仅断开本地通道）
LOCAL_PORT="${LOCAL_PORT:-18080}"
PIDS=$(lsof -nP -iTCP:"${LOCAL_PORT}" -sTCP:LISTEN -t 2>/dev/null | sort -u)
if [ -z "$PIDS" ]; then
  echo "本机 ${LOCAL_PORT} 上没有运行的隧道"
else
  echo "关闭隧道进程: $(echo $PIDS | tr '\n' ' ')"
  kill $PIDS 2>/dev/null
  sleep 1
  lsof -nP -iTCP:"${LOCAL_PORT}" -sTCP:LISTEN >/dev/null 2>&1 \
    && echo "仍有监听，请手工检查" || echo "隧道已关闭，服务器上的 TiDB/demo 服务不受影响"
fi
