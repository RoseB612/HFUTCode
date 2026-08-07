# HFUT 版本上线评估与部署记录

日期：2026-08-07

项目：`SynCode`

目标仓库建议名：`syncode-hfut-ai-interview`

## 1. 上线前评估结论

当前项目已经可以编译通过，`oj-friend` 模块的简历分析主链路具备上线试运行基础。

但它距离“正式生产可长期稳定运行”还差这些能力：

- 全局限流：现在只对简历上传和重分析做了用户级日限额，还没有网关级 IP 限流、接口分级限流。
- 权限与审计：简历接口已按当前登录用户隔离数据，但还缺管理员审计、敏感操作日志。
- 监控告警：还没有接 Prometheus、Grafana、日志采集、异常告警。
- HTTPS：服务器还需要域名、证书、Nginx 反向代理。
- 备份：PostgreSQL、MinIO、Redis 都需要定期备份策略。
- CI/CD：GitHub Actions 或服务器拉取部署流程还没有完全打通。
- 功能完整度：简历分析已经落地，知识库管理、知识库题库面试、文字模拟面试还需要继续补业务接口。

## 2. 本次已经完成的代码改造

### 2.1 PostgreSQL

将 `oj-friend` 的本地和 swarm 配置切到 PostgreSQL：

- `oj-modules/oj-friend/src/main/resources/application-local.yml`
- `oj-modules/oj-friend/src/main/resources/application-swarm.yml`

新增简历分析表结构：

- `deploy/dev/sql/2026-08-05-ai-interview-postgres.sql`

选 PostgreSQL 的原因：

- 适合后续扩展 JSON、全文检索、向量检索能力。
- 比 MySQL 更适合作为 AI 简历分析、知识库元数据、题库标签等结构化和半结构化数据的主库。
- 当前先不引入独立向量库，避免 4C4G 服务器压力过大。

### 2.2 MinIO 文件存储

新增 MinIO 通用组件：

- `oj-common/oj-common-file/src/main/java/com/sintao/common/file/config/MinioProperties.java`
- `oj-common/oj-common-file/src/main/java/com/sintao/common/file/config/MinioConfig.java`
- `oj-common/oj-common-file/src/main/java/com/sintao/common/file/domain/MinioResult.java`
- `oj-common/oj-common-file/src/main/java/com/sintao/common/file/service/MinioService.java`

同时将旧 OSS 自动配置改为可选启用，避免默认启动时仍然强依赖 OSS：

- `oj-common/oj-common-file/src/main/java/com/sintao/common/file/config/OSSConfig.java`
- `oj-common/oj-common-file/src/main/java/com/sintao/common/file/service/OSSService.java`

选 MinIO 的原因：

- 私有化部署简单，适合学生项目或中小型项目先上线试运行。
- 和 S3 协议兼容，后续迁移到云厂商对象存储成本较低。
- 不需要把文件直接塞进数据库，避免 PostgreSQL 存储大文件导致备份和查询变重。

没有继续用 OSS 的原因：

- OSS 依赖云厂商账号、密钥和公网配置，不利于本次服务器快速私有部署。
- 当前用户已经指定文件存储使用 MinIO。

### 2.3 Spring AI 简历分析

新增简历分析接口：

- `POST /resume/upload`
- `GET /resume/list`
- `GET /resume/{resumeId}`
- `POST /resume/{resumeId}/reanalyze`
- `DELETE /resume/{resumeId}`
- `GET /resume/health`

核心代码位置：

- `oj-modules/oj-friend/src/main/java/com/sintao/friend/controller/resume/ResumeController.java`
- `oj-modules/oj-friend/src/main/java/com/sintao/friend/service/resume/impl/ResumeServiceImpl.java`

处理流程：

1. 用户上传简历文件。
2. 文件写入 MinIO。
3. Apache Tika 提取简历文本。
4. 优先调用 Spring AI 做结构化分析。
5. AI 不可用时使用启发式 fallback，保证接口可用。
6. PostgreSQL 保存简历记录和分析日志。

选 Spring AI 的原因：

- 和 Spring Boot 项目集成自然。
- 可以用统一接口切换 OpenAI 兼容模型。
- 比自己封装 HTTP SDK 更利于后续做 Prompt、模型配置、Advisor、RAG 编排。

没有直接手写 HTTP 调模型的原因：

- 后续要做知识库问答和模拟面试，手写 HTTP 会让模型编排逻辑越来越散。
- Spring AI 更适合把 Chat、Embedding、RAG 等能力逐步统一。

### 2.4 Redis 限流

新增用户级日限额：

- 简历上传：默认每天 10 次。
- 简历重分析：默认每天 20 次。
- 简历文件大小：默认最大 10 MB。

配置项：

- `RESUME_UPLOAD_MAX_FILE_SIZE_MB`
- `RESUME_UPLOAD_DAILY_LIMIT`
- `RESUME_REANALYZE_DAILY_LIMIT`

落地方式：

- 使用已有 `RedisService.increment` 做日计数。
- Redis key 按用户、动作、日期隔离。
- 第一次计数时设置过期时间到次日零点。

为什么先不用 Sentinel / Bucket4j / 网关限流：

- 当前项目最容易被滥用的是 AI 分析接口，先在业务层做用户级限额，改动小、上线风险低。
- 网关级限流更适合后续接入完整网关、Nginx、认证链路后统一做。
- Sentinel / Bucket4j 引入成本更高，当前阶段收益不如 Redis 日限额直接。

## 3. 本次新增部署文件

轻量部署目录：

- `deploy/hfut/docker-compose.yml`
- `deploy/hfut/runtime.env.example`
- `deploy/hfut/start.sh`
- `deploy/hfut/README.md`

这套部署只启动：

- PostgreSQL
- Redis
- RabbitMQ
- MinIO
- `oj-friend`

为什么不直接使用原有 swarm：

- 原有 `deploy/prod/swarm/stack.yml` 面向完整集群，包括前端、网关、系统服务、判题服务、训练 Agent 等。
- 你的服务器是 4C4G，直接跑完整栈压力较大。
- 当前先上线 AI 简历分析试运行，轻量 compose 更合适。

## 4. 编译验证

已经执行：

```bash
mvn -pl oj-modules/oj-friend -am -DskipTests compile
```

结果：

```text
BUILD SUCCESS
```

说明当前代码层面不会因为本次改动导致 `oj-friend` 编译失败。

## 5. 服务器需要的环境

服务器系统：Ubuntu

建议安装：

- Git
- JDK 17
- Maven 3.9+
- Docker
- Docker Compose plugin

端口建议：

- `9202`：`oj-friend` 服务
- `9000`：MinIO API
- `9001`：MinIO 控制台
- `15672`：RabbitMQ 管理后台

生产更安全的做法：

- PostgreSQL `5432` 和 Redis `16379` 不建议对公网开放。
- MinIO、RabbitMQ 管理端口建议只允许自己 IP 访问。
- 最终应该用 Nginx + HTTPS 暴露业务接口。

## 6. 服务器上线步骤

### 6.1 构建后端

```bash
mvn -pl oj-modules/oj-friend -am -DskipTests package
```

### 6.2 准备运行时变量

```bash
cp deploy/hfut/runtime.env.example deploy/hfut/runtime.env
```

然后修改 `deploy/hfut/runtime.env`：

- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `RABBITMQ_DEFAULT_PASS`
- `MINIO_SECRET_KEY`
- `MINIO_PUBLIC_BASE_URL`
- `OPENAI_API_KEY`
- `OPENAI_BASE_URL`

### 6.3 启动服务

```bash
sh deploy/hfut/start.sh
```

### 6.4 验证服务

```bash
curl http://127.0.0.1:9202/resume/health
```

预期返回包含 `ok`。

## 7. GitHub 创建仓库状态

本次目标仓库建议名：

```text
syncode-hfut-ai-interview
```

当前环境检查结果：

- 没有安装 GitHub CLI：`gh` 不存在。
- Git Credential Manager 存在，但当前 credential store 不能读取 GitHub 登录态。
- 环境变量里没有 `GITHUB_TOKEN` 或 `GH_TOKEN`。

因此，本会话里还不能自动创建 `RoseB612/syncode-hfut-ai-interview` 仓库并推送。

需要补齐任一条件后即可继续：

- 安装并登录 `gh`，执行 `gh auth login`。
- 或提供一个具备 `repo` 权限的 GitHub Token，设置到 `GITHUB_TOKEN`。
- 或你手动在 GitHub 创建空仓库，然后我把本地 remote 改过去并推送。

## 8. 服务器部署状态

服务器信息来自你提供的截图：

- IPv4：`124.223.30.115`
- 用户：`ubuntu`
- 配置：4 CPU / 4 GB RAM / 40 GB SSD / 3 Mbps

当前本地到服务器的 22 端口连通性检查超时，说明至少存在以下一种情况：

- 云服务器安全组未开放 22。
- 服务器防火墙未放行 22。
- SSH 服务未启动。
- 当前网络环境无法访问该机器的 22 端口。

因此，本会话里暂时无法直接 SSH 上服务器完成部署。

## 9. 下一步

要真正完成“推 GitHub + 上服务器”，还需要：

1. 打通 GitHub 认证或先创建空仓库。
2. 确认服务器 22 端口可访问。
3. 在服务器安装 Docker、JDK、Maven、Git。
4. 拉取仓库。
5. 执行 `mvn package`。
6. 执行 `sh deploy/hfut/start.sh`。
7. 访问 `/resume/health` 验证。
