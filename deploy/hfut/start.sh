#!/usr/bin/env sh
set -eu

docker compose -f deploy/hfut/docker-compose.yml up -d postgres redis rabbitmq minio
docker compose -f deploy/hfut/docker-compose.yml run --rm minio-init
docker compose -f deploy/hfut/docker-compose.yml up -d friend
