# HFUT Lightweight Deployment

This folder contains the minimal runtime setup for the `oj-friend` service on a small Ubuntu server.

## What It Starts

- PostgreSQL 16
- Redis 7
- RabbitMQ 3
- MinIO
- `oj-friend`

## First Boot

1. Build the application JAR:
   - `mvn -pl oj-modules/oj-friend -am -DskipTests package`

2. Create and edit runtime variables:
   - `cp deploy/hfut/runtime.env.example deploy/hfut/runtime.env`

3. Start the runtime:
   - `sh deploy/hfut/start.sh`

4. Verify the service:
   - `curl http://127.0.0.1:9202/resume/health`

## Important Notes

- The MinIO bucket is created automatically.
- The Postgres schema for resume analysis is initialized from `deploy/dev/sql/2026-08-05-ai-interview-postgres.sql`.
- The compose file is meant for the small 4C4G server and does not bring up the whole original swarm stack.
