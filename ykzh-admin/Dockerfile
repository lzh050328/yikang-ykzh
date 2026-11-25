# 基础镜像：与 pom.xml 的 java.version 保持一致（17），否则 UnsupportedClassVersionError
FROM eclipse-temurin:17-jdk-jammy

# 设定时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 环境变量：application-prod.yml 里用 ${OSS_ACCESS_KEY_ID} / ${DEEPSEEK_API_KEY} 引用，
# 变量名必须完全一致，否则容器启动时解析不到占位符会直接失败。
# 安全提示：不要把真实密钥写进 Dockerfile——镜像会随分发泄露。
#           推荐启动容器时通过 --env-file 或 -e 注入（见 .env.example），
#           下方仅保留空值占位。
ENV OSS_ACCESS_KEY_ID=
ENV OSS_ACCESS_KEY_SECRET=
ENV DEEPSEEK_API_KEY=

# Ollama 跑在宿主机上，容器内不能用 localhost 访问。
# 通过 host.docker.internal 或自定义网络网关指向宿主机，端口 11434。
# 前提：① Ollama 监听 0.0.0.0:11434 ② 防火墙放行该端口
ENV AI_OLLAMA_BASE_URL=http://host.docker.internal:11434

# 工作目录：logback 的 log.path=./logs 会落在 /app/logs，deploy.sh 已挂载
WORKDIR /app

# 拷贝jar包
COPY ykzh-admin.jar /app/app.jar

# 暴露端口（与 application-prod.yml 的 server.port、deploy.sh 的 -p 一致）
EXPOSE 9000

# 入口
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
