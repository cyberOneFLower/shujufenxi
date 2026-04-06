#!/usr/bin/env bash
set -euo pipefail
# 等同机内 RabbitMQ 就绪后再启动应用（supervisord 已先拉起 rabbitmq-server）
export ARB_MQ_ENABLED="${ARB_MQ_ENABLED:-true}"
export ARB_MQ_HOST="${ARB_MQ_HOST:-127.0.0.1}"
export ARB_MQ_PORT="${ARB_MQ_PORT:-5672}"
export ARB_MQ_USERNAME="${ARB_MQ_USERNAME:-guest}"
export ARB_MQ_PASSWORD="${ARB_MQ_PASSWORD:-guest}"
: "${JAVA_OPTS:=-XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0}"
for _ in $(seq 1 90); do
  if nc -z 127.0.0.1 5672 2>/dev/null; then
    exec java ${JAVA_OPTS} -jar /app/app.jar
  fi
  sleep 1
done
echo "RabbitMQ did not accept connections on 127.0.0.1:5672 in time" >&2
exit 1
