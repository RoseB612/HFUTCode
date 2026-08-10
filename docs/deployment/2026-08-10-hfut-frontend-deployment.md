# HFUT Frontend Deployment Log

Date: 2026-08-10

## Goal

Expose the existing SynCode frontend pages on the HFUT cloud server while keeping the Java backend API available on port `8080`.

## Frontend Code Found

- `frontend/apps/web`: public landing page.
- `frontend/apps/app`: user-facing app under `/app`, including login, problems, workspace, training, exams, settings, and profile pages.
- `frontend/apps/admin`: admin console under `/admin`, including users, questions, exams, and notices.
- `frontend/packages/api`: shared API client and backend base URL resolution.

## Code Changes

1. Updated `deploy/hfut/docker-compose.yml`.
   - Added `web`, `app`, and `admin` Next.js services.
   - Added a Docker Nginx frontend gateway.
   - Kept backend `friend` on internal port `9202` and public host port from `FRIEND_HOST_PORT`.
   - Configured frontend server-side calls to use Docker internal URL `http://friend:9202`.
   - Configured browser-visible backend URL through `NEXT_PUBLIC_BACKEND_BASE_URL`.

2. Updated `deploy/prod/docker/next-app.Dockerfile`.
   - Added build-time args for `APP_URL`, `ADMIN_URL`, `NEXT_PUBLIC_BACKEND_BASE_URL`, and `SYNCODE_BACKEND_BASE_URL`.
   - Switched Node base image from Docker Hub `node:22-alpine` to `mirror.ccs.tencentyun.com/library/node:22-alpine` because the server timed out pulling from Docker Hub.

3. Updated `deploy/hfut/runtime.env.example`.
   - Added `FRONTEND_HOST_PORT`.
   - Added `NEXT_PUBLIC_BACKEND_BASE_URL`.

4. Updated `deploy/hfut/README.md`.
   - Documented the frontend services and verification URLs.
   - Added the host-Nginx bridge mode for servers where port `80` is already occupied.

## Local Verification

1. Checked Docker Compose rendering:

```bash
docker compose -f deploy/hfut/docker-compose.yml config
```

2. Installed frontend dependencies:

```bash
cd frontend
npm ci
```

3. Built all frontend apps:

```bash
npm run build:web
npm run build:app
npm run build:admin
```

Result: all three Next.js production builds passed.

## GitHub

Pushed changes to:

```text
https://github.com/RoseB612/HFUTCode.git
```

Branch:

```text
codex/hfut-production
```

Commits:

- `021ca4e Deploy HFUT frontend apps`
- `a719eb1 Use mirror node image for frontend builds`

## Server Deployment Steps

1. Created an archive from the local committed source.

```bash
git archive --format=tar.gz -o HFUTCode.tar.gz HEAD
```

2. Uploaded the archive to the server:

```bash
scp HFUTCode.tar.gz ubuntu@124.223.30.115:/home/ubuntu/HFUTCode.tar.gz
```

3. Extracted the new source into a timestamped new directory.

4. Preserved the existing server runtime file:

```text
/home/ubuntu/HFUTCode/deploy/hfut/runtime.env
```

This avoids overwriting database passwords, Redis password, RabbitMQ password, MinIO credentials, and AI provider settings.

5. Replaced `/home/ubuntu/HFUTCode` with the new source and kept the previous source as a timestamped backup.

6. Set these runtime values on the server:

```env
FRIEND_HOST_PORT=8080
FRONTEND_HOST_PORT=127.0.0.1:18080
NEXT_PUBLIC_BACKEND_BASE_URL=http://124.223.30.115:8080
```

The frontend gateway uses `127.0.0.1:18080` because host Nginx already occupies public port `80`.

7. Built the Java backend:

```bash
cd /home/ubuntu/HFUTCode
mvn -pl oj-modules/oj-friend -am -DskipTests package
```

Result: Maven build succeeded.

8. Built and started Docker services:

```bash
sudo -n env DOCKER_HOST=unix:///run/docker.sock docker compose \
  --env-file deploy/hfut/runtime.env \
  -f deploy/hfut/docker-compose.yml \
  up -d --build --force-recreate nginx
```

Result:

- `syncode-web` started.
- `syncode-app` started.
- `syncode-admin` started.
- `syncode-nginx` started on `127.0.0.1:18080`.
- `syncode-friend` continued to expose backend API on public `8080`.

9. Updated host Nginx default site to proxy public port `80` to Docker frontend gateway:

```nginx
location / {
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_pass http://127.0.0.1:18080;
}
```

The original host Nginx default site was backed up to:

```text
/home/ubuntu/nginx-default.before-syncode
```

## Verification

Server-side checks returned:

```text
http://127.0.0.1/                         -> 200 text/html
http://127.0.0.1/app                      -> 200 text/html
http://127.0.0.1:8080/resume/health       -> 200
http://124.223.30.115/                    -> 200
http://124.223.30.115:8080/resume/health  -> 200
```

Running containers:

```text
syncode-nginx      127.0.0.1:18080->80/tcp
syncode-web        3000/tcp
syncode-app        3000/tcp
syncode-admin      3000/tcp
syncode-friend     0.0.0.0:8080->9202/tcp
syncode-postgres   127.0.0.1:5432->5432/tcp
syncode-redis      127.0.0.1:16379->6379/tcp
syncode-rabbitmq   127.0.0.1:5672->5672/tcp
syncode-minio      127.0.0.1:9000-9001->9000-9001/tcp
```

## Public URLs

- Landing page: `http://124.223.30.115/`
- User app: `http://124.223.30.115/app`
- Admin app: `http://124.223.30.115/admin`
- Backend health: `http://124.223.30.115:8080/resume/health`

## Notes

- If public access from a local machine fails but server-side `curl http://124.223.30.115/` returns `200`, check the cloud security group and local network path first.
- Keep PostgreSQL, Redis, RabbitMQ, and MinIO bound to `127.0.0.1`; only Nginx `80` and backend `8080` need public exposure for this lightweight deployment.
- The frontend dependency audit currently reports vulnerabilities from npm packages. They did not block the build, but should be reviewed separately before a formal production launch.
