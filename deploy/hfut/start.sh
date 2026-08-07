#!/usr/bin/env sh
set -eu

ENV_FILE=deploy/hfut/runtime.env

if [ ! -f "$ENV_FILE" ]; then
  cp deploy/hfut/runtime.env.example "$ENV_FILE"
fi

docker compose --env-file "$ENV_FILE" -f deploy/hfut/docker-compose.yml up -d postgres redis rabbitmq minio
docker compose --env-file "$ENV_FILE" -f deploy/hfut/docker-compose.yml run --rm minio-init
docker compose --env-file "$ENV_FILE" -f deploy/hfut/docker-compose.yml up -d friend
