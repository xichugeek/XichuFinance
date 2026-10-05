# SERVER_PREFLIGHT — 只读服务器审计

审计日期：2026-10-06，Asia/Shanghai。

**服务器资源审计 PASS；生产部署 NOT VERIFIED。** SSH 使用用户提供的连接资料和默认端口 22，并核对本机已有的主机密钥。没有修改服务器配置、DNS、容器、数据库或生产数据。

## 实际环境

| 项目 | 只读检查结果 |
| --- | --- |
| 系统 | Ubuntu 24.04.5 LTS，Linux 6.8.0-31-generic |
| CPU | 10 个逻辑 CPU |
| 内存 | 总计 7,939 MiB，可用约 6,631 MiB；Swap 1,023 MiB，未使用 |
| 根目录磁盘 | 总计 433 GB，已用 8.6 GB，可用 403 GB |
| Docker / Compose | 29.8.2 / 5.5.1 |
| 主机监听 | SSH 22；Caddy 80/443；现有 Demo 仅 127.0.0.1:18082 |
| 入口 | 容器 `xg-gateway-caddy-1`，Caddy 2.11.4 |
| 入口网络 | 已有外部网络 `xg_edge` |
| 新项目路径 | `/opt/xichufinance` 尚不存在 |
| 新备份路径 | `/opt/xichugeek/backups/finance` 尚不存在 |

## 现有服务及路由

5 个容器在审计时均为 healthy：

| 容器 | 作用 | 数据 / 入口 |
| --- | --- | --- |
| `xg-gateway-caddy-1` | HTTPS 网关 | 80/443；共享 `xg_edge` |
| `xg-blog-nginx-1` | 官网 Web 服务 | 容器内部 80 |
| `xg-blog-php-1` | 官网 PHP | 容器内部 9000 |
| `xg-blog-db-1` | 官网 MySQL 8.0.45 | 容器内部 3306 |
| `xg-demo-enterprise-site` | 企业 Demo | 主机 loopback 18082；共享 `xg_edge` |

现有 `/opt/xichugeek/gateway/Caddyfile` 包含：

- `xichugeek.com` → `blog-nginx:80`。
- `www.xichugeek.com` → 官网 HTTPS 重定向。
- `enterprise.demo.xichugeek.com` → `xg-demo-enterprise-site:80`。

官网、官网别名重定向后和企业 Demo 的 HTTPS 均实测 HTTP 200，使用正常证书验证。企业 Demo 的 A 记录指向同一服务器。没有把未检查的 Forge 或其他域名标为 PASS。

Caddyfile 为 **单文件只读 bind mount**，不是整个配置目录的挂载。获准部署时必须先保存原文件，再在原 inode 上写入新增内容并执行 validate/reload。通过 rename 替换宿主机文件可能让容器继续读取旧 inode，应避免这种做法。

原 Caddyfile SHA256：`013440c162bb5f72653505879db2ed47c55c8b5d535b7de6bc921af24a409d17`。这里只记录校验值和必要路由；SSH 密码及完整私有服务器配置没有进入仓库。

已通过服务器现有 Caddy 的 `adapt` 命令从 stdin 校验原配置和完整候选配置。候选配置保留原有三个域名，并增加 Finance 域名；没有写入配置文件、执行 validate/reload 或申请新证书。这只是语法转换验证，生产行为仍待部署验收。

## DNS 与备份

公共 DNS 查询显示 `finance-api.demo.xichugeek.com` 返回 **NXDOMAIN**，尚不能签发、验证该域名的 HTTPS。拟添加 A 记录：主机名 `finance-api.demo`，地址 `117.55.232.77`。修改前仍需用户确认；如果使用 CDN 代理，需另外验证转发和证书配置。

已有 `xichugeek-backup.timer`。最近一次服务结果为 `success`、退出码 0，开始时间 2026-10-04 19:15:16 UTC。观察到数据库 `.sql.gz`、WordPress 和配置归档；最新数据库归档约 47 KB，最新 WordPress 归档约 39 MB。

**现有备份恢复未验证。** 审计只检查文件元数据及任务结果，没有读取、恢复、删除归档。Finance 将使用独立的 PostgreSQL dump 和专用私有备份目录。

## 待批准的部署范围

具体配置见 [生产 Compose](../docker-compose.prod.yml)、[Finance Caddy 路由](../deploy/Caddyfile.finance) 和 [部署步骤与回滚](SERVER_DEPLOYMENT.md)。

- 新建独立 Compose 项目 `xichufinance-prod`，2 个容器，内存上限合计 1 GiB、CPU 上限合计 2 个逻辑 CPU。
- 新建项目内网和 `xichufinance-prod_postgres_data` 数据卷；PostgreSQL 不加入 `xg_edge`。
- API 加入已有网关网络，仅通过唯一别名 `xichufinance-api:8000` 提供给 Caddy，不发布主机端口。
- 为现有 Caddy 追加一个 Finance 域名块，验证后 reload；保存原文件用于回滚。
- 生成 Finance 独立生产 Secret，存放于权限 600 的 `.env.production`。
- HTTPS 验收只使用获准的虚构测试账号、流水和 CSV。

添加新容器和入口路由需要按用户原始要求第 40 节及最终安全要求取得确认。
