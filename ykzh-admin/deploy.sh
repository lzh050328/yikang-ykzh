#!/bin/bash
# 部署脚本：用法  sh deploy.sh <服务名> <镜像tag>
# 例：sh deploy.sh ykzh-admin latest
set -e

container_name=$1
image_name=$1
image_tag=$2

if [ -z "$container_name" ] || [ -z "$image_tag" ]; then
  echo "用法: sh deploy.sh <服务名> <镜像tag>"
  exit 1
fi

# 端口：和 Dockerfile 的 EXPOSE、application-prod.yml 的 server.port 保持一致
APP_PORT=${SERVER_PORT:-9000}

# 宿主机目录（日志、上传文件、环境变量文件）
# 默认 /usr/local/<服务名>，可用环境变量 DEPLOY_BASE_DIR 覆盖；
# 若目录建不出来（比如 Jenkins 用户没有 /usr/local 写权限），自动回退到 $HOME
BASE_DIR=${DEPLOY_BASE_DIR:-/usr/local/${container_name}}
if ! mkdir -p "$BASE_DIR/logs" "$BASE_DIR/uploadPath" 2>/dev/null; then
  echo "警告: 无法创建 ${BASE_DIR}（权限不足），回退到 $HOME/${container_name}"
  BASE_DIR="$HOME/${container_name}"
  mkdir -p "$BASE_DIR/logs" "$BASE_DIR/uploadPath"
fi
ENV_FILE=${BASE_DIR}/.env
LOG_DIR=${BASE_DIR}/logs
UPLOAD_DIR=${BASE_DIR}/uploadPath
echo "部署目录: ${BASE_DIR}"

# 1. 停止并删除同名容器（用 name=^/xxx$ 精确匹配，避免误杀名字相近的容器）
old_id=$(docker ps -a -q -f "name=^/${container_name}$")
if [ -n "$old_id" ]; then
  echo "停止并删除旧容器: $container_name ($old_id)"
  docker stop "$old_id" >/dev/null 2>&1 || true
  docker rm "$old_id" >/dev/null 2>&1 || true
else
  echo "容器 $container_name 不存在，直接创建"
fi

# 2. 环境变量：优先用 .env 文件，没有就用 application-prod.yml 里的默认值
#    依次找：部署目录/.env  →  $HOME/服务名/.env  →  当前目录/.env
if [ ! -f "$ENV_FILE" ] && [ -f "$HOME/${container_name}/.env" ]; then
  ENV_FILE="$HOME/${container_name}/.env"
fi
if [ ! -f "$ENV_FILE" ] && [ -f "$(pwd)/.env" ]; then
  ENV_FILE="$(pwd)/.env"
fi
ENV_ARGS=""
if [ -f "$ENV_FILE" ]; then
  echo "使用环境变量文件: $ENV_FILE"
  ENV_ARGS="--env-file ${ENV_FILE}"
else
  echo "警告: 未找到 .env（找过 ${BASE_DIR}/.env、$HOME/${container_name}/.env、$(pwd)/.env）"
  echo "      将直接使用镜像内 Dockerfile 里 ENV 定义的值"
fi

# 3. 启动容器
echo "启动容器 $container_name (镜像 $image_name:$image_tag, 端口 $APP_PORT)"
docker run -d --restart=always \
  --name "$container_name" \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SERVER_PORT="$APP_PORT" \
  ${ENV_ARGS} \
  -v "${LOG_DIR}:/app/logs" \
  -v "${UPLOAD_DIR}:/home/ruoyi/uploadPath" \
  -p "${APP_PORT}:${APP_PORT}" \
  "${image_name}:${image_tag}"

# 4. 等待并校验启动结果（避免 Jenkins 显示成功、实际已崩）
echo "等待应用启动..."
sleep 20
running=$(docker inspect -f '{{.State.Running}}' "$container_name" 2>/dev/null || echo "false")
if [ "$running" != "true" ]; then
  echo "容器启动失败，最近 200 行日志："
  docker logs --tail 200 "$container_name" || true
  exit 1
fi

echo "容器已启动，最近 60 行日志："
docker logs --tail 60 "$container_name" || true
