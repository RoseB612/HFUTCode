# HFUT 前端部署记录

日期：2026-08-10

## 目标

把 SynCode 项目中已有的前端页面部署到 HFUT 云服务器，同时保留 Java 后端 API 的公网访问端口 `8080`。

最终访问方式：

- 官网首页：`http://124.223.30.115/`
- 用户端主应用：`http://124.223.30.115/app`
- 后台管理端：`http://124.223.30.115/admin`
- 后端健康检查：`http://124.223.30.115:8080/resume/health`

## 找到的前端代码

前端代码在 `frontend` 目录下，是一个 Next.js monorepo。

- `frontend/apps/web`：官网首页。
- `frontend/apps/app`：用户端应用，访问路径是 `/app`，包含登录、题目列表、编程工作台、训练、考试、设置、个人中心等页面。
- `frontend/apps/admin`：后台管理端，访问路径是 `/admin`，包含用户管理、题目管理、考试管理、公告管理等页面。
- `frontend/packages/api`：共享 API 请求封装和后端地址解析逻辑。
- `frontend/packages/ui`：共享 UI 组件。
- `frontend/packages/config`：共享导航和产品配置。
- `frontend/packages/tokens`：共享设计变量。

## 修改的代码

### 1. 修改 HFUT Docker Compose

修改文件：`deploy/hfut/docker-compose.yml`

新增服务：

- `web`：构建并运行 `@aioj/web` 官网。
- `app`：构建并运行 `@aioj/app` 用户端。
- `admin`：构建并运行 `@aioj/admin` 后台端。
- `nginx`：Docker 内部前端网关，负责把 `/`、`/app`、`/admin` 分发到对应 Next.js 服务。

保留服务：

- `friend`：Java 后端服务，容器内端口 `9202`，公网端口由 `FRIEND_HOST_PORT` 控制。
- `postgres`：PostgreSQL。
- `redis`：Redis。
- `rabbitmq`：RabbitMQ。
- `minio`：MinIO。

前端服务的后端访问方式：

- 服务端请求使用 Docker 内网地址：`http://friend:9202`
- 浏览器侧请求使用公网后端地址：`NEXT_PUBLIC_BACKEND_BASE_URL`

### 2. 修改前端 Dockerfile

修改文件：`deploy/prod/docker/next-app.Dockerfile`

主要变化：

- 支持通过 `APP_NAME` 构建不同前端应用。
- 支持构建期注入：
  - `APP_URL`
  - `ADMIN_URL`
  - `NEXT_PUBLIC_BACKEND_BASE_URL`
  - `SYNCODE_BACKEND_BASE_URL`
  - `NEXT_PUBLIC_BACKEND_SERVICE_PREFIX`
  - `SYNCODE_BACKEND_SERVICE_PREFIX`
- 把基础镜像从 Docker Hub 的 `node:22-alpine` 改成腾讯云镜像源：

```text
mirror.ccs.tencentyun.com/library/node:22-alpine
```

这样做的原因是服务器拉取 Docker Hub 镜像时出现超时：

```text
failed to resolve source metadata for docker.io/library/node:22-alpine
```

使用腾讯云镜像源后，服务器可以正常拉取 Node 镜像并完成前端构建。

### 3. 修改运行环境示例

修改文件：`deploy/hfut/runtime.env.example`

新增变量：

```env
FRONTEND_HOST_PORT=80
NEXT_PUBLIC_BACKEND_BASE_URL=http://127.0.0.1:9202
NEXT_PUBLIC_BACKEND_SERVICE_PREFIX=
SYNCODE_BACKEND_SERVICE_PREFIX=
```

其中 `NEXT_PUBLIC_BACKEND_SERVICE_PREFIX` 和 `SYNCODE_BACKEND_SERVICE_PREFIX` 用来适配不同部署模式：

- 完整网关模式：可以使用 `/friend`。
- HFUT 轻量部署：前端直连 `oj-friend`，所以服务前缀为空。

### 4. 修改 API 路径兼容逻辑

修改文件：

- `frontend/packages/api/src/runtime.ts`
- `frontend/packages/api/src/client.ts`
- `frontend/apps/app/src/app/api/user/avatar/route.ts`

问题背景：

前端原来很多请求路径写成了 `/friend/...`，这是完整网关部署下的路径。例如：

```text
/friend/user/sendCode
/friend/question/semiLogin/list
/friend/message/semiLogin/list
```

但 HFUT 轻量部署中，前端直接访问 `oj-friend` 服务。`oj-friend` 的真实接口路径是：

```text
/user/sendCode
/question/semiLogin/list
/message/semiLogin/list
```

因此需要在前端 API 层支持“服务前缀可配置”。

修复后：

- 默认仍然保留 `/friend`，兼容完整网关模式。
- HFUT 部署时把服务前缀设置为空，自动把 `/friend/user/sendCode` 转成 `/user/sendCode`。
- WebSocket 地址也同步适配，例如从 `/friend/ws/judge/result` 转成 `/ws/judge/result`。

## 本地验证

### 1. 检查 Docker Compose 配置

```bash
docker compose -f deploy/hfut/docker-compose.yml config
```

结果：配置可以正常解析。

### 2. 安装前端依赖

```bash
cd frontend
npm ci
```

说明：

本地第一次执行构建时，因为没有 `node_modules`，所以 `next` 命令不存在。执行 `npm ci` 后恢复正常。

`npm ci` 提示有 7 个依赖漏洞，这是前端 npm 依赖审计问题，不影响当前构建和运行，但正式生产前建议单独评估。

### 3. 构建三个前端应用

```bash
npm run build:web
npm run build:app
npm run build:admin
```

结果：

- `@aioj/web` 构建成功。
- `@aioj/app` 构建成功。
- `@aioj/admin` 构建成功。

## GitHub 推送

仓库地址：

```text
https://github.com/RoseB612/HFUTCode.git
```

分支：

```text
codex/hfut-production
```

相关提交：

```text
021ca4e Deploy HFUT frontend apps
a719eb1 Use mirror node image for frontend builds
a6bd819 Document HFUT frontend deployment
```

## 服务器部署过程

### 1. 打包本地代码

```bash
git archive --format=tar.gz -o HFUTCode.tar.gz HEAD
```

### 2. 上传到服务器

```bash
scp HFUTCode.tar.gz ubuntu@124.223.30.115:/home/ubuntu/HFUTCode.tar.gz
```

### 3. 解压新版本

服务器上把新代码解压到带时间戳的新目录。

### 4. 保留运行时配置

服务器已有运行时配置文件：

```text
/home/ubuntu/HFUTCode/deploy/hfut/runtime.env
```

部署时保留这个文件，避免覆盖：

- 数据库密码
- Redis 密码
- RabbitMQ 密码
- MinIO 账号和密码
- OpenAI 配置
- 其他服务器专属运行参数

### 5. 备份旧代码

旧的 `/home/ubuntu/HFUTCode` 被移动为带时间戳的备份目录。

这样如果新版本有问题，可以回滚到旧目录。

### 6. 设置运行时变量

服务器上的关键运行时变量：

```env
FRIEND_HOST_PORT=8080
FRONTEND_HOST_PORT=127.0.0.1:18080
NEXT_PUBLIC_BACKEND_BASE_URL=http://124.223.30.115:8080
NEXT_PUBLIC_BACKEND_SERVICE_PREFIX=
SYNCODE_BACKEND_SERVICE_PREFIX=
```

`FRONTEND_HOST_PORT` 使用 `127.0.0.1:18080` 的原因：

服务器宿主机已经有 Nginx 占用了公网 `80` 端口。为了不直接停止宿主机 Nginx，Docker 内部前端网关只监听本机 `18080`，再由宿主机 Nginx 反代过去。

### 7. 构建 Java 后端

```bash
cd /home/ubuntu/HFUTCode
mvn -pl oj-modules/oj-friend -am -DskipTests package
```

结果：构建成功。

### 8. 构建并启动 Docker 服务

```bash
sudo -n env DOCKER_HOST=unix:///run/docker.sock docker compose \
  --env-file deploy/hfut/runtime.env \
  -f deploy/hfut/docker-compose.yml \
  up -d --build --force-recreate nginx
```

结果：

- `syncode-web` 启动成功。
- `syncode-app` 启动成功。
- `syncode-admin` 启动成功。
- `syncode-nginx` 启动成功，监听 `127.0.0.1:18080`。
- `syncode-friend` 继续通过公网 `8080` 暴露后端 API。

### 9. 配置宿主机 Nginx

宿主机 Nginx 的公网 `80` 端口反代到 Docker 前端网关：

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

原来的宿主机 Nginx 默认配置已备份到：

```text
/home/ubuntu/nginx-default.before-syncode
```

## 验证结果

服务器侧检查结果：

```text
http://127.0.0.1/                         -> 200 text/html
http://127.0.0.1/app                      -> 200 text/html
http://127.0.0.1:8080/resume/health       -> 200
http://124.223.30.115/                    -> 200
http://124.223.30.115:8080/resume/health  -> 200
```

运行中的容器：

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

## 发送验证码问题定位

现象：

用户端登录页点击“发送验证码”后提示“服务器繁忙”。

排查步骤：

1. 查看前端登录页，确认点击按钮调用 `/app/api/auth/send-code`。
2. 查看 Next.js API 路由，确认它转发到后端 `/friend/user/sendCode`。
3. 查看服务器 `syncode-friend` 日志，发现异常：

```text
请求地址 '/friend/user/sendCode'，发生异常。
NoResourceFoundException: No static resource friend/user/sendCode.
```

根因：

`oj-friend` 当前是轻量部署，服务本身没有 `/friend` 上下文路径；真实接口是 `/user/sendCode`。前端按完整网关模式请求 `/friend/user/sendCode`，所以后端找不到接口，最终被全局异常处理成“服务器繁忙”。

修复方向：

在前端 API 客户端增加“后端服务前缀”配置。HFUT 部署时服务前缀为空，自动把 `/friend/...` 转成真实路径。

## 注意事项

- 如果浏览器访问公网 IP 失败，但服务器内部 `curl http://124.223.30.115/` 返回 `200`，优先检查云服务器安全组和本地网络。
- PostgreSQL、Redis、RabbitMQ、MinIO 继续只绑定 `127.0.0.1`，不要暴露到公网。
- 当前轻量部署对外暴露：
  - `80`：前端页面。
  - `8080`：后端 API。
- npm 依赖审计中有漏洞提示，后续正式生产前建议单独做依赖升级评估。
