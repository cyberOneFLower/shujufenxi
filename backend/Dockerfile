# 构建阶段
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN apk add --no-cache maven \
  && mvn -q -DskipTests package \
  && mv target/monitor-*.jar target/app.jar

# 运行阶段
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S arb && adduser -S arb -G arb
COPY --from=build /src/target/app.jar /app/app.jar
RUN mkdir -p /app/data && chown -R arb:arb /app
USER arb
EXPOSE 8787
VOLUME ["/app/data"]
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
