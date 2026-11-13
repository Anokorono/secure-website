# ============================================================
# 多阶段构建：先编译，再运行，镜像体积最小化
# ============================================================

# ---- 阶段一：构建 ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 先复制 pom.xml 以利用 Docker 层缓存（依赖变化才重新下载）
COPY pom.xml .
RUN mvn -q dependency:go-offline

# 复制源码并打包
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- 阶段二：运行 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 非 root 用户运行，降低容器逃逸风险
RUN groupadd -r app && useradd -r -g app app

# 复制打包产物
COPY --from=builder /app/target/secure-website-0.1.0.jar app.jar

USER app
EXPOSE 8080

# 数据库密码通过环境变量注入（启动时通过 -e DB_PASSWORD=xxx 传入）
ENTRYPOINT ["java", "-jar", "app.jar"]
