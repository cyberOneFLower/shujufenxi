# syntax=docker/dockerfile:1
# 单镜像：前端 + Spring Boot + 同容器 RabbitMQ（supervisord；Rabbit 使用官方镜像脚本，可稳定前台运行）
# 构建：docker build -t arb-monitor:1.0.0 .
# 运行：docker run --rm -p 8787:8787 -p 15672:15672 -p 5672:5672 -v arb-data:/app/data -v arb-mq:/var/lib/rabbitmq arb-monitor:1.0.0

FROM node:22-alpine AS web
WORKDIR /web
ENV NPM_CONFIG_REGISTRY=https://registry.npmmirror.com
COPY web/ ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci 2>/dev/null || npm install
RUN npm run build

FROM eclipse-temurin:17-jdk-alpine AS backend
WORKDIR /src
COPY backend/pom.xml .
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2/repository \
    mkdir -p /root/.m2 \
    && printf '%s\n' \
      '<?xml version="1.0" encoding="UTF-8"?>' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>aliyunmaven</id>' \
      '      <mirrorOf>central</mirrorOf>' \
      '      <url>https://maven.aliyun.com/repository/public</url>' \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' \
      > /root/.m2/settings.xml \
    && apk add --no-cache maven \
    && mvn -q -DskipTests package \
    && mv target/monitor-*.jar target/app.jar

# --- 运行阶段：官方 rabbitmq:management（前台启动可靠）+ OpenJDK + supervisord ---
FROM rabbitmq:3.13-management

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless \
    supervisor \
    netcat-openbsd \
    bash \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN groupadd -r arb && useradd -r -g arb -d /app -s /usr/sbin/nologin arb

COPY docker/wait-rabbit-and-run.sh /app/wait-rabbit-and-run.sh
COPY docker/supervisord-rabbit.conf /etc/supervisor/conf.d/arb.conf
RUN sed -i 's/\r$//' /app/wait-rabbit-and-run.sh && chmod +x /app/wait-rabbit-and-run.sh

COPY --from=backend /src/target/app.jar /app/app.jar
RUN mkdir -p /app/data && chown -R arb:arb /app

EXPOSE 8787 5672 15672
VOLUME ["/app/data", "/var/lib/rabbitmq"]

ENV RABBITMQ_DEFAULT_USER=guest \
    RABBITMQ_DEFAULT_PASS=guest \
    ARB_LIVE_ENABLED=true \
    ARB_MQ_ENABLED=true \
    ARB_MQ_HOST=127.0.0.1 \
    ARB_MQ_PORT=5672 \
    ARB_MQ_USERNAME=guest \
    ARB_MQ_PASSWORD=guest \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0"

CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/supervisord.conf"]
