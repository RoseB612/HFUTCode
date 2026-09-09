# hfut.top 域名上线配置

本文说明如何把 `hfut.top` 绑定到云服务器 `124.223.30.115`，并让前端和后端分别通过域名访问。

## 一、DNS 解析

在 `hfut.top` 的域名服务商控制台新增以下记录：

| 主机记录 | 记录类型 | 记录值 | 用途 |
|---|---|---|---|
| `@` | A | `124.223.30.115` | `hfut.top` 主域名 |
| `www` | CNAME | `hfut.top` | `www.hfut.top` |
| `api` | A | `124.223.30.115` | `api.hfut.top` 后端入口 |

DNS 记录里只填写 IP 或域名，不能填写 `http://`，也不能填写端口号。

DNS 生效后：

```powershell
nslookup hfut.top
nslookup www.hfut.top
nslookup api.hfut.top
```

三个域名都应该解析到 `124.223.30.115`。解析生效时间取决于 DNS 服务商和 TTL，通常几分钟到几十分钟，最长可能需要 24 到 48 小时。

## 二、云服务器安全组

云服务器安全组建议只开放：

| 端口 | 协议 | 用途 |
|---:|---|---|
| 22 | TCP | SSH，建议只允许自己的公网 IP |
| 80 | TCP | HTTP，供证书签发和 HTTP 跳转使用 |
| 443 | TCP | HTTPS，正式业务入口 |

`8080` 和 `9202` 是后端调试或宿主机映射端口，正式上线不建议对公网开放。确认 Nginx 反向代理正常后，应在安全组和服务器防火墙中限制这两个端口，只允许本机或 Docker 网络访问。

## 三、仓库中的 Nginx 配置

配置文件：

```text
deploy/prod/nginx/conf.d/default.conf
```

已经加入：

- `hfut.top`、`www.hfut.top`：转发到前端页面；
- `api.hfut.top`：转发到 Docker 内部的 `friend:9202`；
- 基本的真实 IP、转发协议和超时请求头。

Docker Compose 中的 Nginx 和 `friend` 位于同一个 Compose 网络，所以 Nginx 可以通过 `friend:9202` 访问 Java 服务，不需要把 Java 端口直接暴露给浏览器。

当前服务器使用宿主机 Nginx 占用 80 端口，再转发到 Docker Nginx 的 `127.0.0.1:18080`。对应的可复用配置已经放在：

```text
deploy/prod/nginx/host-site.conf
```

服务器应用方式：

```bash
sudo cp /etc/nginx/sites-available/default /etc/nginx/sites-available/default.bak
sudo cp deploy/prod/nginx/host-site.conf /etc/nginx/sites-available/default
sudo nginx -t
sudo systemctl reload nginx
```

## 四、前端 API 地址

生产环境构建前端时，浏览器可访问的后端地址建议设置为：

```env
NEXT_PUBLIC_BACKEND_BASE_URL=https://api.hfut.top
```

服务端容器之间仍然使用：

```env
SYNCODE_BACKEND_BASE_URL=http://friend:9202
```

修改 `NEXT_PUBLIC_BACKEND_BASE_URL` 后需要重新构建并启动前端镜像，因为 Next.js 的 `NEXT_PUBLIC_*` 变量通常会在构建阶段写入浏览器代码。

## 五、先用 HTTP 验证

DNS 生效并重新部署 Nginx 后，在本地执行：

```powershell
curl.exe -I http://hfut.top/
curl.exe -I http://www.hfut.top/
curl.exe -i http://api.hfut.top/resume/health
```

预期结果：

- 前端域名返回 `200` 或合理的重定向；
- API 健康检查返回 HTTP `200`，响应内容包含成功状态。

如果返回 `502`，优先在服务器执行：

```bash
docker compose -f /home/ubuntu/HFUTCode/deploy/hfut/docker-compose.yml ps
docker logs --tail 200 syncode-nginx
docker logs --tail 200 syncode-friend
docker exec syncode-nginx nginx -t
```

## 六、配置 HTTPS

正式使用建议启用 HTTPS。可以在宿主机安装 Certbot，也可以使用云厂商证书服务。证书至少覆盖：

- `hfut.top`
- `www.hfut.top`
- `api.hfut.top`

证书签发前必须确保 80 端口从公网可访问。证书签发后，将证书挂载到 Nginx，并把 80 端口配置为 301 跳转到 HTTPS。

最终访问地址建议为：

```text
https://hfut.top
https://www.hfut.top
https://api.hfut.top/resume/health
```

## 七、部署顺序

1. 在 DNS 控制台创建上面的 A 和 CNAME 记录。
2. 等待 `nslookup` 解析到 `124.223.30.115`。
3. 在 DNS 控制台创建 `api.hfut.top` 记录后，再设置 `NEXT_PUBLIC_BACKEND_BASE_URL=https://api.hfut.top`。
4. 重新构建前端和后端镜像。
5. 执行 `nginx -t` 检查配置。
6. 重启 Compose 服务。
7. 用三个 `curl` 命令验证前端、www 和 API。
8. 申请并配置 HTTPS。
9. HTTPS 验证通过后，关闭公网的 `8080`、`9202`。
