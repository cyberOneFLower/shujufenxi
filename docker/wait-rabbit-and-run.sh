#!/usr/bin/env bash
set -euo pipefail
# 同机 RabbitMQ 就绪后再启动应用；若 SPRING_DATASOURCE_URL 指向其它容器上的 MySQL（如 Compose 的 mysql 服务），再等待该主机端口可连，避免 Hikari 抢跑失败被 supervisord 判死。
# 默认激活 docker profile（application-docker.yml），避免未注入 SPRING_DATASOURCE_* 时仍连 127.0.0.1。
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-docker}"
export ARB_MQ_ENABLED="${ARB_MQ_ENABLED:-true}"
export ARB_MQ_HOST="${ARB_MQ_HOST:-127.0.0.1}"
export ARB_MQ_PORT="${ARB_MQ_PORT:-5672}"
export ARB_MQ_USERNAME="${ARB_MQ_USERNAME:-guest}"
export ARB_MQ_PASSWORD="${ARB_MQ_PASSWORD:-guest}"
# 避免宿主机/环境带入代理导致 JDBC 走 SOCKS（日志栈里出现 SocksSocketImpl 时常见）
for _v in http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy; do
  unset "$_v" 2>/dev/null || true
done
: "${JAVA_OPTS:=-XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0}"
JAVA_OPTS="${JAVA_OPTS} -Djava.net.useSystemProxies=false -DsocksProxyHost= -DsocksProxyPort=0"

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
elif [[ -z "$jdbc" ]]; then
  # 未注入 JDBC 时仍使用 application-docker.yml 默认 mysql:3306
  wait_tcp mysql 3306 "MySQL (docker profile default)" 120
fi

exec java ${JAVA_OPTS} -jar /app/app.jar
