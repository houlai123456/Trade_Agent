FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /build

# 复制 pom.xml 并下载依赖（利用 Docker 缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# 运行时镜像
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装 curl（用于健康检查）
RUN apk add --no-cache curl

# 从构建阶段复制 jar 文件
COPY --from=build /build/target/*.jar app.jar

# 创建日志目录
RUN mkdir -p /var/log/trading_helper

# 暴露端口
EXPOSE 8080

# JVM 参数通过环境变量 JAVA_OPTS 传入
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/trading_helper/"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
