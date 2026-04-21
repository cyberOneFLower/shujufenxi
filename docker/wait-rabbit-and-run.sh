#!/usr/bin/env bash
set -euo pipefail
# 同机 RabbitMQ 就绪后再启动应用；若 SPRING_DATASOURCE_URL 指向其它容器上的 MySQL（如 Compose 的 mysql 服务），再等待该主机端口可连，避免 Hikari 抢跑失败被 supervisord 判死。
export ARB_MQ_ENABLED="${ARB_MQ_ENABLED:-true}"
export ARB_MQ_HOST="${ARB_MQ_HOST:-127.0.0.1}"
export ARB_MQ_PORT="${ARB_MQ_PORT:-5672}"
export ARB_MQ_USERNAME="${ARB_MQ_USERNAME:-guest}"
export ARB_MQ_PASSWORD="${ARB_MQ_PASSWORD:-guest}"
: "${JAVA_OPTS:=-XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0}"

wait_tcp() {
  local host="$1" port="$2" label="$3" max="${4:-120}"
  local i=0
  echo "Waiting for ${label} at ${host}:${port}..."
  while [ "$i" -lt "$max" ]; do
    if nc -z "$host" "$port" 2>/dev/null; then
      echo "${label} is accepting connections."
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  echo "Timeout waiting for ${label} at ${host}:${port}" >&2
  return 1
}

for _ in $(seq 1 90); do
  if nc -z 127.0.0.1 5672 2>/dev/null; then
    break
  fi
  sleep 1
done
if ! nc -z 127.0.0.1 5672 2>/dev/null; then
  echo "RabbitMQ did not accept connections on 127.0.0.1:5672 in time" >&2
  exit 1
fi

jdbc="${SPRING_DATASOURCE_URL:-}"
if [[ -n "$jdbc" && "$jdbc" =~ ^jdbc:mysql://([^/:]+)(:([0-9]+))?/ ]]; then
  mh="${BASH_REMATCH[1]}"
  mp="${BASH_REMATCH[3]:-3306}"
  case "$mh" in
    127.0.0.1|localhost) ;;
    *)
      wait_tcp "$mh" "$mp" "MySQL" 120
      ;;
  esac
fi

exec java ${JAVA_OPTS} -jar /app/app.jar
