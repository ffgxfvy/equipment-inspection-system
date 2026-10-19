# ============================================================
# 后端镜像：多阶段构建
#   阶段 1 用 maven 镜像编译（构建期需要网络拉依赖）
#   阶段 2 只保留 JRE + jar，镜像体积小很多
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先只拷 pom 再拉依赖，利用 Docker 层缓存：改代码时不会重新下载依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 时区：容器默认 UTC，不设置的话日志时间与业务时间会差 8 小时
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /build/target/*.jar app.jar

# 上传目录挂出来持久化
VOLUME ["/app/uploads"]

EXPOSE 8080

# prod profile 走 MySQL；同时给 JVM 一个合理的堆上限，避免容器里被 OOM Kill
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
