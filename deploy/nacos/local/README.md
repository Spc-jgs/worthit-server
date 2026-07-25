# WorthIt 本地 Nacos 配置基线

本目录中的五个 YAML 文件与 Nacos Data ID 一一对应，仅保存可审阅的本地
非敏感配置。运行时使用 namespace `worthit-local`、group
`WORTHIT_LOCAL`。数据库、Redis、Nacos 凭据、JWT Secret、Same-Token 值和
第三方密钥不得写入这些模板。

四个服务在启用 `local-infra` Profile 时依次导入：

1. `worthit-common.yaml`
2. 与 `spring.application.name` 同名的服务配置

## 同步

先确认 Nacos 服务端和控制台均已就绪：

```bash
scripts/local-infra/nacos-config.sh check
```

随后幂等创建 namespace 并覆盖五个受控 Data ID，再进行字节级验证：

```bash
scripts/local-infra/nacos-config.sh sync
scripts/local-infra/nacos-config.sh verify
```

查看四个服务的健康实例数量：

```bash
scripts/local-infra/nacos-config.sh services
```

默认服务端基址为 `http://127.0.0.1:8848/nacos`，控制台基址为
`http://127.0.0.1:8080`。可分别使用 `NACOS_SERVER_BASE_URL` 和
`NACOS_CONSOLE_BASE_URL` 覆盖。Nacos 开启认证时必须同时提供
`NACOS_USERNAME`、`NACOS_PASSWORD`；脚本不会输出凭据、访问令牌或配置正文。

脚本只管理上述五个 Data ID，不提供删除操作。
