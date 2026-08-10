# HFUT Lightweight Deployment

This folder contains the minimal runtime setup for the `oj-friend` service on a small Ubuntu server.

## What It Starts

- PostgreSQL 16
- Redis 7
- RabbitMQ 3
- MinIO
- `oj-friend`
- `@aioj/web`
- `@aioj/app`
- `@aioj/admin`
- Nginx frontend gateway

## First Boot

1. Build the application JAR:
   - `mvn -pl oj-modules/oj-friend -am -DskipTests package`

2. Create and edit runtime variables:
   - `cp deploy/hfut/runtime.env.example deploy/hfut/runtime.env`

3. Start the runtime:
   - `sh deploy/hfut/start.sh`
   - or, when the current user is not in the Docker group: `DOCKER_CMD="sudo docker" sh deploy/hfut/start.sh`

4. Verify the service:
   - `curl http://127.0.0.1:9202/resume/health`
   - If `FRIEND_HOST_PORT=8080`, use `curl http://127.0.0.1:8080/resume/health`
   - If `FRONTEND_HOST_PORT=80`, open `http://<server-ip>/`, `http://<server-ip>/app`, and `http://<server-ip>/admin`
   - If host Nginx already owns port `80`, set `FRONTEND_HOST_PORT=127.0.0.1:18080` and proxy host Nginx to `http://127.0.0.1:18080`.

## Important Notes

- The MinIO bucket is created automatically.
- The Postgres schema for resume analysis is initialized from `deploy/dev/sql/2026-08-05-ai-interview-postgres.sql`.
- Elasticsearch is optional in this lightweight setup; the question services fall back to PostgreSQL when the ES repository is disabled.
- The compose file is meant for the small 4C4G server and does not bring up the whole original swarm stack.
- PostgreSQL, Redis, RabbitMQ, and MinIO are bound to `127.0.0.1` by default. Only the application host port should be exposed publicly.
- The compose file uses mirror-backed images to avoid Docker Hub timeouts on small cloud servers.
- Frontend server-side requests use Docker internal `http://friend:9202`; browser-visible URLs use `NEXT_PUBLIC_BACKEND_BASE_URL`.
