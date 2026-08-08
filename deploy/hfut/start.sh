#!/usr/bin/env sh
set -eu

ENV_FILE=deploy/hfut/runtime.env
DOCKER_CMD=${DOCKER_CMD:-docker}

if [ ! -f "$ENV_FILE" ]; then
  cp deploy/hfut/runtime.env.example "$ENV_FILE"
fi

$DOCKER_CMD compose --env-file "$ENV_FILE" -f deploy/hfut/docker-compose.yml up -d postgres redis rabbitmq minio
$DOCKER_CMD compose --env-file "$ENV_FILE" -f deploy/hfut/docker-compose.yml up -d friend
