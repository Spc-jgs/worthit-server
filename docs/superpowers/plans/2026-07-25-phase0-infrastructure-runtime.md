# WorthIt Phase 0 基础设施运行时实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. 每个任务必须按测试/验证先行、最小实现、局部门禁、精确暂存、提交的顺序执行。

**Goal:** 让 Gateway、Auth、Tracking、Reminder 在冻结版本线上接入 Nacos
3.0.3、MySQL 8.4、Redis、Flyway、Sa-Token JWT Simple/Same-Token 和
Actuator，并形成可重复自动验证与 dev-stack 真实联合验收。

**Architecture:** 普通单元测试不依赖开发机残留服务；三库 Flyway 与 Redis
兼容性使用隔离 Testcontainers；Nacos 3.0.3 和四服务联合链路使用显式
`local-infra` Spring Profile 与独立 dev-stack。Gateway 保持 WebFlux，三个
业务 App 保持 MVC；Gateway 和下游都校验用户 Token，下游额外校验
Same-Token。Auth 是 Same-Token 唯一刷新责任方。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Cloud 2025.0.3、
Spring Cloud Alibaba 2025.0.0.0、Nacos 3.0.3、MySQL 8.4、Redis 7.4、
Flyway 11.7.2、Sa-Token 1.45.0、Testcontainers 1.21.4、BlockHound
1.0.17.RELEASE、JUnit 5、AssertJ、MockMvc、WebTestClient、Maven。

---

## 0. 执行约束

- 设计基线：
  `docs/superpowers/specs/2026-07-25-phase0-infrastructure-runtime-design.md`。
- 权威文档从 `../docs/README.md` 进入；数据库执行源只复制其登记的 M1
  `V1`，不复制 `tracking_m2/V2`。
- 当前开始点：`main` 与 `origin/main` 均为 `a16c8a6`；已有未提交文件只有
  `AGENTS.md` 和本轮设计稿。执行前必须重新核对，不覆盖新出现的用户改动。
- dev-stack 是独立仓库；只在最终联合验收任务中使用，提交历史不得混入
  WorthIt。
- 所有密码、Nacos 凭据、Redis 密码和 JWT Secret 只从环境变量读取。测试可
  使用随机生成、仅存在于测试进程内的 Secret。
- 每个任务完成后运行列出的门禁，使用精确路径 `git add`，按
  `<type>(<scope>): <中文描述>` 提交。未经单独授权不推送 WorthIt。
- 任何真实服务验证均先记录开始前状态，不停止开始前已运行的容器。

## Task 1: 固化已确认设计和本地中间件协作规则

**Files:**

- Modify: `AGENTS.md`
- Create:
  `docs/superpowers/specs/2026-07-25-phase0-infrastructure-runtime-design.md`

**Step 1: 重新核对工作区边界**

Run:

```bash
git status --short --branch
git diff -- AGENTS.md
git diff --no-index /dev/null \
  docs/superpowers/specs/2026-07-25-phase0-infrastructure-runtime-design.md
```

Expected:

- 分支仍为 `main`；
- 只看到上述两个预期文件；
- AGENTS 明确 dev-stack 是独立仓库、禁止泄密和破坏性清理；
- 设计包含 Nacos 3.0.3、Flyway、Redis/Sa-Token、Actuator、验证边界；
- 设计记录 Sa-Token 1.45 同步 Redis DAO 的上游事实和 Gateway 验证要求。

若出现其他文件，先判断是否为用户新改动；不得将其加入本任务提交。

**Step 2: 执行文档门禁**

Run:

```bash
rg -n 'TODO|TBD|待定|后续确认' \
  AGENTS.md \
  docs/superpowers/specs/2026-07-25-phase0-infrastructure-runtime-design.md
git diff --check
```

Expected: `rg` 无命中，`git diff --check` 退出码为 0。

**Step 3: 提交已确认设计**

```bash
git add AGENTS.md \
  docs/superpowers/specs/2026-07-25-phase0-infrastructure-runtime-design.md
git diff --cached --check
git commit -m "docs(infra): 固化基础设施运行时设计"
```

Expected: 提交只含上述两个文件。

## Task 2: 锁定运行时依赖和 WebFlux/MVC 边界

**Files:**

- Modify: `pom.xml`
- Modify: `worthit-gateway/pom.xml`
- Modify: `worthit-auth/worthit-auth-app/pom.xml`
- Modify: `worthit-tracking/worthit-tracking-app/pom.xml`
- Modify: `worthit-reminder/worthit-reminder-app/pom.xml`
- Modify:
  `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/architecture/GatewayArchitectureTest.java`

**Step 1: 先扩展 Gateway 运行时边界测试**

在 `GatewayArchitectureTest` 的生产类依赖规则中禁止：

```text
jakarta.servlet..
org.springframework.web.servlet..
org.apache.tomcat..
org.springframework.jdbc..
org.flywaydb..
org.springframework.web.client..
```

Run:

```bash
mvn -pl worthit-gateway \
  -Dtest=GatewayArchitectureTest test
```

Expected: PASS，先锁住 Gateway 禁止 Servlet/JDBC/Flyway/阻塞 HTTP Client 的
代码依赖边界。不要用 `Class.forName` 判断 `spring-web` 中的 `RestClient`
是否存在：Gateway 合法依赖的 `spring-web` 本身包含该类型，门禁应限制生产
代码引用和 Maven 模块依赖，而不是误判类路径。

**Step 2: 在根 POM 补齐未由 BOM 暴露的显式版本**

在 properties 增加：

```xml
<blockhound.version>1.0.17.RELEASE</blockhound.version>
```

在 dependencyManagement 增加：

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-jwt</artifactId>
    <version>${sa-token.version}</version>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-template</artifactId>
    <version>${sa-token.version}</version>
</dependency>
<dependency>
    <groupId>io.projectreactor.tools</groupId>
    <artifactId>blockhound</artifactId>
    <version>${blockhound.version}</version>
</dependency>
<dependency>
    <groupId>io.projectreactor.tools</groupId>
    <artifactId>blockhound-junit-platform</artifactId>
    <version>${blockhound.version}</version>
</dependency>
```

Nacos、Flyway、Testcontainers 不重复写版本，分别由 SCA/Boot BOM 管理。

**Step 3: 增加四服务公共运行依赖**

Gateway 增加：

- `worthit-common-web`；
- Nacos Config/Discovery；
- Actuator；
- `sa-token-jwt`；
- `sa-token-redis-template`；
- BlockHound 两个测试依赖。

三个 MVC App 增加：

- Nacos Config/Discovery；
- Actuator；
- `sa-token-jwt`；
- `sa-token-redis-template`；
- `flyway-core`；
- `flyway-mysql`；
- Testcontainers `junit-jupiter` 和 `mysql` 测试依赖。

不要增加 `spring-cloud-starter-bootstrap`；Nacos Config 使用
`spring.config.import`。不要在 Gateway 增加 Flyway、JDBC 或 MySQL。

**Step 4: 验证解析版本和边界**

Run:

```bash
mvn -pl worthit-gateway,worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  dependency:tree \
  -Dincludes=com.alibaba.nacos:nacos-client,org.flywaydb:flyway-core,\
cn.dev33:sa-token-jwt,cn.dev33:sa-token-redis-template,\
org.testcontainers:testcontainers,io.projectreactor.tools:blockhound

mvn -pl worthit-gateway \
  -Dtest=GatewayValidationRuntimeTest,GatewayArchitectureTest test
```

Expected:

- `nacos-client:3.0.3`；
- `flyway-core:11.7.2`；
- `sa-token-jwt:1.45.0`；
- `sa-token-redis-template:1.45.0`；
- `testcontainers:1.21.4`；
- `blockhound:1.0.17.RELEASE`；
- Gateway 边界测试通过。

**Step 5: 提交**

```bash
git add pom.xml \
  worthit-gateway/pom.xml \
  worthit-auth/worthit-auth-app/pom.xml \
  worthit-tracking/worthit-tracking-app/pom.xml \
  worthit-reminder/worthit-reminder-app/pom.xml \
  worthit-gateway/src/test/java/com/shaopc/worthit/gateway/architecture/GatewayArchitectureTest.java
git diff --cached --check
git commit -m "build(infra): 接入基础设施运行依赖"
```

## Task 3: 冻结四服务配置、端口和探针契约

**Files:**

- Create: `worthit-gateway/src/main/resources/application.yml`
- Create:
  `worthit-gateway/src/main/resources/application-local-infra.yml`
- Modify:
  `worthit-auth/worthit-auth-app/src/main/resources/application.yml`
- Create:
  `worthit-auth/worthit-auth-app/src/main/resources/application-local-infra.yml`
- Modify:
  `worthit-tracking/worthit-tracking-app/src/main/resources/application.yml`
- Create:
  `worthit-tracking/worthit-tracking-app/src/main/resources/application-local-infra.yml`
- Modify:
  `worthit-reminder/worthit-reminder-app/src/main/resources/application.yml`
- Create:
  `worthit-reminder/worthit-reminder-app/src/main/resources/application-local-infra.yml`
- Create four app-local `RuntimeConfigurationContractTest.java` files under
  each module’s matching `src/test/java/.../config/` package.

**Step 1: 先写配置契约测试**

每个测试使用 `YamlPropertySourceLoader` 读取 `application.yml` 和
`application-local-infra.yml`，断言：

```java
assertThat(base.getProperty("spring.application.name"))
        .isEqualTo("worthit-auth"); // 每模块替换
assertThat(base.getProperty("server.port")).isEqualTo(18081);
assertThat(base.getProperty(
        "management.endpoint.health.probes.enabled")).isEqualTo(true);
assertThat(local.getProperty("spring.config.import[0]"))
        .asString().contains("nacos:worthit-common.yaml");
assertThat(local.getProperty("spring.cloud.nacos.config.namespace"))
        .isEqualTo("${NACOS_NAMESPACE:worthit-local}");
```

另读取资源原文，断言不包含：

```java
assertThat(yaml).doesNotContain(
        "password: root",
        "password: 123456",
        "jwt-secret-key: worthit",
        "spring.redis.");
```

Run:

```bash
mvn -pl worthit-gateway,worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -Dtest='*RuntimeConfigurationContractTest' test
```

Expected: FAIL，因为 Gateway 配置和四个 `local-infra` 文件尚不存在。

**Step 2: 写安全的基础配置**

冻结：

| Service | name | port |
| --- | --- | ---: |
| Gateway | `worthit-gateway` | 18080 |
| Auth | `worthit-auth` | 18081 |
| Tracking | `worthit-tracking` | 18082 |
| Reminder | `worthit-reminder` | 18083 |

每个基础配置启用：

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  endpoints:
    web:
      exposure:
        include: health,info
```

保留现有 springdoc 默认关闭、`local | dev | test` 显式开启的行为。

**Step 3: 写 `local-infra` 配置**

公共 Nacos 配置形状：

```yaml
spring:
  config:
    import:
      - nacos:worthit-common.yaml?group=${NACOS_GROUP:WORTHIT_LOCAL}&refreshEnabled=true
      - nacos:${spring.application.name}.yaml?group=${NACOS_GROUP:WORTHIT_LOCAL}&refreshEnabled=true
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      username: ${NACOS_USERNAME}
      password: ${NACOS_PASSWORD}
      config:
        namespace: ${NACOS_NAMESPACE:worthit-local}
      discovery:
        namespace: ${NACOS_NAMESPACE:worthit-local}
        group: ${NACOS_GROUP:WORTHIT_LOCAL}
```

三个 App 的 datasource URL 指向各自逻辑库；数据库、Redis、JWT 密码/Secret
均使用无默认值占位符：

```yaml
spring:
  datasource:
    username: ${WORTHIT_AUTH_DB_USERNAME}
    password: ${WORTHIT_AUTH_DB_PASSWORD}
  data:
    redis:
      password: ${WORTHIT_REDIS_PASSWORD}
sa-token:
  jwt-secret-key: ${WORTHIT_SA_TOKEN_JWT_SECRET}
```

Tracking/Reminder 分别替换为自己的
`WORTHIT_TRACKING_DB_*`/`WORTHIT_REMINDER_DB_*`。如果本地 Redis 明确无
密码，不提交空密码默认值；由运行命令显式提供空值或对应 dev-stack 变量。

**Step 4: 运行测试**

Run:

```bash
mvn -pl worthit-gateway,worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -Dtest='*RuntimeConfigurationContractTest,*OpenApi*Test' test
```

Expected: PASS。

**Step 5: 提交**

精确暂存四模块的八个配置文件和四个测试文件，提交：

```bash
git commit -m "feat(config): 冻结四服务本地运行配置"
```

## Task 4: 落地 Flyway 唯一执行源和 MySQL 8.4 空库门禁

**Files:**

- Create:
  `worthit-auth/worthit-auth-app/src/main/resources/db/migration/V1__init_auth.sql`
- Create:
  `worthit-tracking/worthit-tracking-app/src/main/resources/db/migration/V1__init_tracking.sql`
- Create:
  `worthit-reminder/worthit-reminder-app/src/main/resources/db/migration/V1__init_reminder.sql`
- Create: `scripts/verify-flyway-source-parity.sh`
- Create:
  `worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/migration/AuthFlywayMigrationTest.java`
- Create:
  `worthit-tracking/worthit-tracking-app/src/test/java/com/shaopc/worthit/tracking/app/migration/TrackingFlywayMigrationTest.java`
- Create:
  `worthit-reminder/worthit-reminder-app/src/test/java/com/shaopc/worthit/reminder/app/migration/ReminderFlywayMigrationTest.java`

**Step 1: 先写三个失败的迁移测试**

每个测试使用：

```java
@Testcontainers
class AuthFlywayMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4");

    @Test
    void migratesVersionOneOnEmptyMysql84Database() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        MYSQL.getJdbcUrl(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).hasToString("1");
    }
}
```

各测试再用 JDBC metadata 断言本服务关键表。Tracking 额外执行：

```sql
SELECT COUNT(*)
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'trk_item'
  AND index_name = 'uk_item_source_wish'
  AND column_name = 'source_wish_id'
```

Run:

```bash
mvn -pl worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -Dtest='*FlywayMigrationTest' test
```

Expected: FAIL，classpath 下没有 migration。

**Step 2: 字节级复制 M1 V1**

从权威目录机械复制：

```bash
cp ../docs/数据库文档/flyway/auth/V1__init_auth.sql \
  worthit-auth/worthit-auth-app/src/main/resources/db/migration/V1__init_auth.sql
cp ../docs/数据库文档/flyway/tracking/V1__init_tracking.sql \
  worthit-tracking/worthit-tracking-app/src/main/resources/db/migration/V1__init_tracking.sql
cp ../docs/数据库文档/flyway/reminder/V1__init_reminder.sql \
  worthit-reminder/worthit-reminder-app/src/main/resources/db/migration/V1__init_reminder.sql
```

复制后立刻使用 `shasum -a 256` 对比。不得手工重排 SQL、补空行或修改注释。

**Step 3: 写哈希一致性门禁**

`scripts/verify-flyway-source-parity.sh`：

- `set -euo pipefail`；
- 从脚本位置解析仓库根目录；
- 权威 docs 默认是 `../docs`，允许
  `WORTHIT_DOCS_DIR` 覆盖；
- docs 或任一执行源缺失立即失败；
- 使用 `cmp -s` 做字节一致比较；
- 只比较 auth/tracking/reminder 三个 M1 V1；
- 输出文件名和 `OK`，不输出 SQL 内容。

Run:

```bash
bash -n scripts/verify-flyway-source-parity.sh
bash scripts/verify-flyway-source-parity.sh
```

Expected: 三个文件均 `OK`。

**Step 4: 运行空库测试**

Run:

```bash
mvn -pl worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -Dtest='*FlywayMigrationTest' test
```

Expected: MySQL 8.4 容器启动，三项 PASS；Tracking 的
`source_wish_id` 唯一索引存在。

**Step 5: 提交**

```bash
git add scripts/verify-flyway-source-parity.sh \
  worthit-auth/worthit-auth-app/src/main/resources/db/migration \
  worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/migration \
  worthit-tracking/worthit-tracking-app/src/main/resources/db/migration \
  worthit-tracking/worthit-tracking-app/src/test/java/com/shaopc/worthit/tracking/app/migration \
  worthit-reminder/worthit-reminder-app/src/main/resources/db/migration \
  worthit-reminder/worthit-reminder-app/src/test/java/com/shaopc/worthit/reminder/app/migration
git diff --cached --check
git commit -m "feat(db): 接入三服务 Flyway 空库迁移"
```

## Task 5: 建立 Nacos 3.0.3 配置模板和幂等同步工具

**Files:**

- Create: `deploy/nacos/local/README.md`
- Create: `deploy/nacos/local/worthit-common.yaml`
- Create: `deploy/nacos/local/worthit-gateway.yaml`
- Create: `deploy/nacos/local/worthit-auth.yaml`
- Create: `deploy/nacos/local/worthit-tracking.yaml`
- Create: `deploy/nacos/local/worthit-reminder.yaml`
- Create: `scripts/local-infra/nacos-config.sh`
- Create:
  `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/config/NacosTemplateContractTest.java`

**Step 1: 先写模板契约测试**

测试枚举五个 YAML，断言：

- 文件均存在且可解析；
- 不含 `password`、`secret`、`token` 的实际值；
- common 只含公共超时、日志、Actuator 和探针属性；
- Gateway 只含路由/安全横切配置；
- Auth 独占 Same-Token rotation 配置；
- Tracking 独占 Reminder Client 超时；
- Reminder 不含 Tracking 数据库或任务配置。

Run:

```bash
mvn -pl worthit-gateway -Dtest=NacosTemplateContractTest test
```

Expected: FAIL，模板不存在。

**Step 2: 写五个非敏感模板**

模板只保存环境差异和开关。不要保存：

- 数据库/Redis/Nacos 密码；
- JWT Secret；
- Same-Token 值；
- 微信 AppSecret；
- 生产域名或生产 namespace。

Gateway 路由使用 `lb://worthit-auth`、`lb://worthit-tracking`、
`lb://worthit-reminder`，不写 localhost 下游。

**Step 3: 写 Nacos 3 同步工具**

支持：

```bash
scripts/local-infra/nacos-config.sh check
scripts/local-infra/nacos-config.sh sync
scripts/local-infra/nacos-config.sh verify
scripts/local-infra/nacos-config.sh services
```

固定使用 Nacos 3 API，并区分服务端与控制台端口：

- 服务端基址默认 `http://127.0.0.1:8848/nacos`；
- 控制台基址默认 `http://127.0.0.1:8080`；
- server readiness：
  `GET /nacos/v3/admin/core/state/readiness`；
- console readiness：`GET /v3/console/health/readiness`；
- namespace 查询/创建：
  `/nacos/v3/admin/core/namespace/check` 与
  `/nacos/v3/admin/core/namespace`；
- 配置发布/读取：`/nacos/v3/admin/cs/config`；
- 实例读取：`/nacos/v3/admin/ns/instance/list`。

参数名使用 Nacos 3.0.3 源码契约：

- namespace check：`namespaceId`；
- namespace create：`namespaceId`、`namespaceName`、`namespaceDesc`；
- config：`dataId`、`groupName`、`namespaceId`、`content`、`type=yaml`；
- instances：`namespaceId`、`groupName`、`serviceName`、
  `healthyOnly=true`。

工具：

- 可分别通过 `NACOS_SERVER_BASE_URL` 和 `NACOS_CONSOLE_BASE_URL`
  覆盖服务端/控制台基址；
- 默认 namespace/group 为 `worthit-local`/`WORTHIT_LOCAL`；
- 使用 `curl --fail-with-body --silent --show-error`；
- 使用 `jq` 只判断 `code==0` 和返回数据；
- Nacos 开启认证时只从 `NACOS_USERNAME/NACOS_PASSWORD` 获取凭据；
- 不回显密码、access token 或配置正文；
- `sync` 幂等创建 namespace、覆盖五个受控 Data ID；
- 不提供 delete 命令。

**Step 4: 验证静态契约**

Run:

```bash
bash -n scripts/local-infra/nacos-config.sh
mvn -pl worthit-gateway -Dtest=NacosTemplateContractTest test
rg -n '(password|secret|token):[[:space:]]+[^$]' deploy/nacos/local
```

Expected: Bash 语法和测试通过，敏感值扫描无命中。

**Step 5: 提交**

```bash
git add deploy/nacos/local scripts/local-infra/nacos-config.sh \
  worthit-gateway/src/test/java/com/shaopc/worthit/gateway/config/NacosTemplateContractTest.java
git diff --cached --check
git commit -m "feat(nacos): 增加本地配置同步基线"
```

## Task 6: 验证 Redis SaTokenDao 和 JWT Simple 兼容性

**Files:**

- Create four service-local `SaTokenRuntimeConfiguration.java` files.
- Create:
  `worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/security/SaTokenRedisCompatibilityTest.java`
- Create:
  `worthit-gateway/src/test/java/com/shaopc/worthit/gateway/security/GatewaySaTokenRedisCompatibilityTest.java`
- Modify four `application-local-infra.yml` files.

**Step 1: 先写 Auth Redis/JWT 失败测试**

使用 `GenericContainer<>("redis:7.4-alpine")` 和
`@DynamicPropertySource` 提供随机端口、测试 JWT Secret。启动最小 Spring
上下文后断言：

```java
assertThat(SaManager.getSaTokenDao())
        .isInstanceOf(SaTokenDaoForRedisTemplate.class);
assertThat(StpUtil.getStpLogic())
        .isInstanceOf(StpLogicJwtForSimple.class);

StpUtil.login(1001L);
String token = StpUtil.getTokenValue();
assertThat(token).isNotBlank();
assertThat(StpUtil.getLoginIdAsLong()).isEqualTo(1001L);
assertThat(redis.keys("worthit-token:*")).isNotEmpty();

StpUtil.logout();
assertThatThrownBy(StpUtil::checkLogin)
        .isInstanceOf(NotLoginException.class);
```

Run:

```bash
mvn -pl worthit-auth/worthit-auth-app \
  -Dtest=SaTokenRedisCompatibilityTest test
```

Expected: FAIL，尚未显式注册 JWT Simple `StpLogic`。

**Step 2: 写四服务运行配置**

每个服务注册：

```java
@Bean
StpLogic stpLogicJwt() {
    return new StpLogicJwtForSimple();
}
```

保持配置类在各运行模块，不放入 `common-security` 自动配置。四服务共享：

- 相同 `sa-token.token-name`；
- 相同 JWT Secret 环境变量；
- 相同 Redis；
- 清晰统一的 key prefix。

不要创建 `common-redis`。

**Step 3: 写 Gateway Redis/Same-Token 测试**

在 Redis Testcontainer 中取得真实 `SaTokenDao`，调用
`SaSameUtil.getToken()` 后断言：

- Redis 出现 `<token-name>:var:same-token`；
- token TTL 为正数；
- `SaTokenSameTokenService` 可获取并校验相同值；
- `TrustedHeadersGlobalFilter` 覆盖伪造 Same-Token。

该测试在测试线程调用同步 DAO，不把“能读 Redis”误写成“Netty 非阻塞”。

**Step 4: 运行兼容性测试和依赖边界**

Run:

```bash
mvn -pl worthit-gateway,worthit-auth/worthit-auth-app \
  -Dtest='*SaTokenRedisCompatibilityTest,GatewayValidationRuntimeTest' test
```

Expected: PASS；Gateway 仍无 Servlet/JDBC/Flyway/RestClient。

**Step 5: 提交**

精确暂存四个运行配置类、两个测试和四个配置文件，提交：

```bash
git commit -m "feat(security): 接入 Redis 会话与 JWT Simple"
```

## Task 7: 实现 Auth 单一 Same-Token 刷新责任

**Files:**

- Create:
  `worthit-auth/worthit-auth-app/src/main/java/com/shaopc/worthit/auth/app/security/sametoken/SameTokenRotationProperties.java`
- Create:
  `.../SameTokenRotationGateway.java`
- Create:
  `.../SaTokenSameTokenRotationGateway.java`
- Create:
  `.../RedisLeaderLock.java`
- Create:
  `.../SameTokenRotationScheduler.java`
- Create:
  `.../SameTokenRotationConfiguration.java`
- Create matching unit/integration tests under
  `worthit-auth-app/src/test/java/.../security/sametoken/`
- Modify: `deploy/nacos/local/worthit-auth.yaml`

**Step 1: 先写调度决策单元测试**

用 fake gateway、fake lock 和 `SimpleMeterRegistry` 覆盖：

1. 剩余 TTL 高于 `refresh-before`：不抢锁、不刷新；
2. TTL 低于阈值且抢锁成功：刷新一次；
3. 抢锁失败：不刷新；
4. 刷新异常：计失败指标，不输出 token；
5. disabled：无任何 Redis 调用；
6. 非法 interval/threshold/lock TTL：配置绑定失败。

接口形状：

```java
interface SameTokenRotationGateway {
    long remainingSeconds();
    void refresh();
}

interface RedisLeaderLock {
    boolean executeIfLeader(String lockName, Duration ttl, Runnable action);
}
```

Run:

```bash
mvn -pl worthit-auth/worthit-auth-app \
  -Dtest='*SameTokenRotation*Test' test
```

Expected: FAIL，生产类不存在。

**Step 2: 写最小刷新实现**

- `SaTokenSameTokenRotationGateway` 只封装
  `SaSameUtil.getTokenTimeout()`/`refreshToken()`；
- `RedisLeaderLock` 使用 Redis `SET NX` + TTL 获取；
- 释放锁使用“比较 owner 后删除”的 Lua，不直接 `DEL` 他人锁；
- owner 使用进程内 UUID，不进入日志；
- Scheduler 只在 Auth 且
  `worthit.security.same-token.rotation.enabled=true` 时装配；
- 不记录 Same-Token 值；
- 指标至少包含 success、skipped、failure 和 remaining TTL；
- 其他三个服务不注册刷新 Scheduler。

**Step 3: 写 Redis 并发集成测试**

使用 Redis Testcontainer 创建两个 Scheduler 实例，同时触发。断言：

- refresh gateway 总调用次数为 1；
- 锁 TTL 存在；
- owner 不匹配时无法删除锁；
- action 异常后属于当前 owner 的锁可释放。

**Step 4: 配置 Nacos 非敏感参数**

`worthit-auth.yaml` 增加 enabled、check interval、refresh-before、lock TTL；
不包含 Same-Token 或 Redis 密码。

**Step 5: 验证并提交**

```bash
mvn -pl worthit-auth/worthit-auth-app \
  -Dtest='*SameTokenRotation*Test,*RedisLeaderLock*Test' test
git diff --check
git add worthit-auth/worthit-auth-app/src/main/java/com/shaopc/worthit/auth/app/security/sametoken \
  worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/security/sametoken \
  deploy/nacos/local/worthit-auth.yaml
git commit -m "feat(auth): 实现 Same-Token 单实例刷新"
```

## Task 8: 接入 Gateway Reactor 与下游 MVC 双重认证

**Files:**

- Create:
  `worthit-gateway/src/main/java/com/shaopc/worthit/gateway/security/GatewaySaTokenConfiguration.java`
- Create:
  `worthit-gateway/src/main/java/com/shaopc/worthit/gateway/security/GatewaySecurityErrorWriter.java`
- Create matching Gateway WebTestClient tests.
- Modify: `worthit-gateway/pom.xml`
- Modify three existing `TrustedSourceFilter.java` files.
- Modify three existing `TrustedSourceFilterTest.java` files.
- Create service-local public API login-check tests using test Controllers.

**Step 1: 先写 Gateway 认证测试**

测试路由包括：

- `/api/v1/auth/wechat/login`：不要求已有用户登录，但仍经过可信头清洗；
- 其他 `/api/**`：无/错用户 Token 返回 HTTP 401 +
  `AUTH_UNAUTHORIZED`；
- 有效 Token：进入下游 chain；
- `/internal/**` 不配置公网 Gateway 路由；
- `/actuator/health/**` 不要求用户登录；
- 伪造 Same-Token、caller、user/session/trace 仍被覆盖或删除。

Run:

```bash
mvn -pl worthit-gateway \
  -Dtest='*GatewaySaToken*Test,TrustedHeadersGlobalFilterTest' test
```

Expected: FAIL，尚无 `SaReactorFilter` Bean 和统一错误 writer。

**Step 2: 实现 Gateway SaReactorFilter**

使用官方 `SaReactorFilter`：

```java
return new SaReactorFilter()
        .addInclude("/api/**")
        .addExclude("/api/v1/auth/wechat/login")
        .setAuth(ignored -> StpUtil.checkLogin())
        .setError(error -> errorWriter.unauthorized(error));
```

错误 writer 输出 `ApiResponse.error(AUTH_UNAUTHORIZED, traceId, List.of())`，
不包含异常类名、堆栈或 token。

`TrustedHeadersGlobalFilter` 仍负责清洗和 Same-Token/TraceId 重建；过滤器顺序
由测试冻结。

**Step 3: 先扩展 MVC 过滤器测试**

对 Auth、Tracking、Reminder：

- `/internal/**`：只验证 Same-Token；
- `/api/**`：先验证 Same-Token，再验证用户登录；
- Auth 登录路径：验证 Same-Token，但跳过已有用户登录；
- 无用户登录返回 401 `AUTH_UNAUTHORIZED`；
- 无/错 Same-Token 返回 403 `AUTH_FORBIDDEN`；
- 身份错误不进入测试 Controller；
- health/docs 仍按既有规则跳过。

使用可注入的用户登录校验接口，避免单元测试绑定静态 `StpUtil`。运行配置使用
Sa-Token 适配器实现该接口。

**Step 4: 实现三个服务自有适配**

保持 Servlet 类型只存在于 App；不移动到 Common。统一行为但允许少量三模块
重复，避免把 MVC Starter 变成服务安全策略容器。

**Step 5: BlockHound 边界测试**

在 Gateway 测试资源增加
`reactor.blockhound.integration.BlockHoundIntegration`：

- 只 allowlist 上游已确认的
  `SaTokenDaoForRedisTemplate` 同步 get/set/timeout 调用；
- 不 allowlist WorthIt 自己的 filter/config 代码；
- Surefire 使用 Java 17 所需
  `-XX:+AllowRedefinitionToAddDeleteMethods`；
- 一个 sentinel 测试证明 parallel scheduler 上 `Thread.sleep` 会被拦截；
- Gateway filter 使用非阻塞 fake provider 时无额外 blocking call。

此测试证明项目没有新增隐藏阻塞；Sa-Token Redis 同步 I/O 作为已知上游事实
留到 Task 10 记录真实指标。

**Step 6: 运行并提交**

```bash
mvn -pl worthit-gateway,worthit-auth/worthit-auth-app,\
worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -Dtest='*Security*Test,*Trusted*Test,*BlockHound*Test' test

git diff --check
git add worthit-gateway/pom.xml worthit-gateway/src \
  worthit-auth/worthit-auth-app/src/main/java/com/shaopc/worthit/auth/app/security \
  worthit-auth/worthit-auth-app/src/test/java/com/shaopc/worthit/auth/app/security \
  worthit-tracking/worthit-tracking-app/src/main/java/com/shaopc/worthit/tracking/app/security \
  worthit-tracking/worthit-tracking-app/src/test/java/com/shaopc/worthit/tracking/app/security \
  worthit-reminder/worthit-reminder-app/src/main/java/com/shaopc/worthit/reminder/app/security \
  worthit-reminder/worthit-reminder-app/src/test/java/com/shaopc/worthit/reminder/app/security
git commit -m "feat(security): 接入网关与下游双重认证"
```

暂存前检查 Gateway `src` 中没有 target、日志或本地配置文件。

## Task 9: 验证 Nacos 注册发现、Gateway 路由和 Tracking→Reminder

**Files:**

- Create:
  `worthit-reminder/worthit-reminder-app/src/main/java/com/shaopc/worthit/reminder/app/infra/LocalInfraProbeController.java`
- Create:
  `worthit-tracking/worthit-tracking-app/src/main/java/com/shaopc/worthit/tracking/infra/LocalInfraReminderProbeClient.java`
- Create:
  `worthit-tracking/worthit-tracking-app/src/main/java/com/shaopc/worthit/tracking/infra/LocalInfraProbeController.java`
- Create matching profile/HTTP interface tests.
- Modify `deploy/nacos/local/worthit-gateway.yaml`
- Modify `deploy/nacos/local/worthit-common.yaml`
- Modify `scripts/local-infra/nacos-config.sh`

**Step 1: 先写 Profile 隔离测试**

断言两个 Probe Controller/Client：

- 无 `local-infra` Profile 时不存在；
- 启用时存在；
- Reminder probe 只在 `/internal/__infra/ping`；
- Tracking probe 通过 `http://worthit-reminder` 的
  `@HttpExchange` 客户端调用；
- 请求带 Same-Token、`X-Caller-Service=worthit-tracking`、TraceId；
- 不复用或伪造真实 `ReminderCommandClient` 业务接口。

Run:

```bash
mvn -pl worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -Dtest='*LocalInfraProbe*Test' test
```

Expected: FAIL，probe 不存在。

**Step 2: 实现仅本地探针**

使用 `@Profile("local-infra")`，响应只包含：

```json
{"service":"worthit-reminder","probe":"ready"}
```

不返回环境变量、Bean、配置全文、token 或数据库信息。

**Step 3: 增加本地 Gateway 探针路由**

只在 `worthit-gateway.yaml` 的本地模板中增加 `/__infra/**` 路由；目标仍使用
`lb://worthit-*`。生产默认配置没有这些路由。

Gateway 访问 Reminder probe 时应：

1. 清洗外部内部头；
2. 注入真实 Same-Token/TraceId；
3. 由 Nacos LoadBalancer 解析实例；
4. 下游 `TrustedSourceFilter` 放行。

**Step 4: 增加配置刷新探针**

`worthit-common.yaml` 增加非敏感
`worthit.runtime.probe-message`。Profile 专属 probe 可返回该单个值，用于
验证 refresh；不得暴露 `/actuator/env` 或 `/configprops`。

**Step 5: 运行自动测试并提交**

```bash
mvn -pl worthit-gateway,worthit-tracking/worthit-tracking-app,\
worthit-reminder/worthit-reminder-app \
  -Dtest='*LocalInfraProbe*Test,*ReminderClientConfigurationTest' test
git diff --check
git add deploy/nacos/local scripts/local-infra/nacos-config.sh \
  worthit-gateway/src \
  worthit-tracking/worthit-tracking-app/src/main/java/com/shaopc/worthit/tracking/infra \
  worthit-tracking/worthit-tracking-app/src/test \
  worthit-reminder/worthit-reminder-app/src/main/java/com/shaopc/worthit/reminder/app/infra \
  worthit-reminder/worthit-reminder-app/src/test
git commit -m "test(infra): 增加注册发现联合探针"
```

暂存前排除未改动测试、target 和本地日志。

## Task 10: dev-stack 四服务真实联合验收

**Files:**

- Create: `docs/runbooks/local-infra.md`
- Create: `scripts/local-infra/verify.sh`
- Create: `docs/evidence/phase0-infra/.gitkeep` only if the repository rules
  require an evidence directory; otherwise evidence stays in the final task
  report and temporary logs are not committed.

**Step 1: 写先失败的联合验证脚本**

`verify.sh` 只读取环境变量并验证：

- Nacos readiness；
- MySQL 三库及 `flyway_schema_history` V1；
- Redis PING；
- 四服务 liveness/readiness；
- Nacos 中四个健康实例；
- Gateway→Auth/Tracking/Reminder local probe；
- Tracking→Reminder local probe；
- 直连缺失/错误 Same-Token 为 403；
- Nacos probe-message 更新后目标服务刷新；
- 响应和日志中没有密码、JWT Secret、Same-Token 值。

启动服务前运行：

```bash
bash -n scripts/local-infra/verify.sh
scripts/local-infra/verify.sh
```

Expected: FAIL，并明确报告第一个尚未就绪的依赖；不得用空成功跳过。

**Step 2: 记录 dev-stack 开始前状态**

在 `/Users/shaopc/Documents/Script/dev-stack` 阅读最新根 README 和
MySQL/Redis/Nacos README，然后记录：

```bash
git status --short --branch
docker compose ps
docker compose --profile nacos config --images
```

确认 Nacos 渲染为 3.0.3。若不是，停止 WorthIt 联合验收并等待 dev-stack
支线完成；不要在 WorthIt 任务里临时改另一个仓库。

**Step 3: 启动最小中间件**

按 dev-stack 最新 README 启动 MySQL、Redis、Nacos。禁止：

- `down -v`；
- prune；
- 删除卷；
- 重建开始前已运行且不属于本任务的服务。

验证：

```bash
docker compose ps
docker compose --profile nacos logs --tail=200 nacos
curl --fail-with-body \
  http://127.0.0.1:8848/nacos/v3/admin/core/state/readiness
curl --fail-with-body \
  http://127.0.0.1:8080/v3/console/health/readiness
```

**Step 4: 初始化三库和 Nacos 配置**

- 使用数据库管理员环境变量幂等创建
  `worthit_auth`、`worthit_tracking`、`worthit_reminder` 和对应账号；
- 不把管理员密码写入命令历史、日志或仓库；
- 运行 `nacos-config.sh sync` 和 `verify`；
- 启动三个 App 后让各自 Flyway 执行 V1。

**Step 5: 启动四服务**

使用 Java 17 和 `local-infra` Profile，日志写到任务专用临时目录。为每个
进程记录 PID；只终止本任务启动的 PID。

启动顺序：

1. Auth（负责 Same-Token 初始化/刷新）；
2. Reminder；
3. Tracking；
4. Gateway。

每个服务必须先通过自身 readiness，再启动依赖它的下一层。

**Step 6: 运行协议级验收**

Run:

```bash
scripts/local-infra/nacos-config.sh services
scripts/local-infra/verify.sh
bash scripts/verify-flyway-source-parity.sh
```

Expected:

- 四个 Nacos 健康实例，端口为 18080–18083；
- 三个 Flyway V1；
- Redis 会话/Same-Token 受控 key 存在但值不显示；
- Gateway 和 Tracking 的两条发现链路成功；
- 错误 Same-Token 被拒绝；
- config refresh 生效；
- liveness/readiness 全部 200。

**Step 7: Gateway Sa-Token Redis 行为验证**

先从 `/actuator/metrics` 查实际 Reactor Netty/Event Loop 指标名，再执行受控
并发：

```bash
seq 1 200 | xargs -P 20 -I{} \
  curl --silent --output /dev/null --write-out '%{http_code} %{time_total}\n' \
  http://127.0.0.1:18080/__infra/reminder/ping
```

记录：

- 成功率；
- p50/p95/max；
- Event Loop pending tasks；
- Redis command latency；
- BlockHound 自动测试结果；
- 是否出现 event-loop starvation、超时或线程堆积。

允许上游 `SaTokenDaoForRedisTemplate` 已知同步 I/O，但不得把结果写成
“完全非阻塞”。若出现明显瓶颈，停止版本锁定结论，形成受控线程隔离或 Gateway
MVC 的独立设计提案；本任务不静默换栈。

**Step 8: 全量回归**

Run:

```bash
mvn test
mvn verify
mvn dependency:tree
bash scripts/verify-flyway-source-parity.sh
git diff --check
git status --short --branch
```

Expected: 全部通过；只存在本任务 runbook/verify 脚本改动。

**Step 9: 写 runbook**

`docs/runbooks/local-infra.md` 必须包含：

- dev-stack 权威路径和先读文档；
- 必需环境变量名，不含值；
- 启动/同步/验证顺序；
- 四服务端口；
- 精确停止本任务 Java PID 和本次启动容器的命令；
- 哪些容器是开始前已有、不得停止；
- 常见诊断顺序：Compose 状态→日志→协议探测→服务注册→应用 readiness。

**Step 10: 提交最终运行工具**

```bash
git add docs/runbooks/local-infra.md scripts/local-infra/verify.sh
git diff --cached --check
git commit -m "docs(infra): 补充本地联合验收手册"
```

不要提交临时日志、PID、凭据、访问 Token、真实 Same-Token 或本机环境快照。

## Task 11: 最终质量门禁与交付

**Files:**

- Modify only if evidence exposes a real defect; do not perform opportunistic
  refactoring.

**Step 1: 验证提交范围**

```bash
git status --short --branch
git log --oneline --decorate -12
git diff origin/main...HEAD --stat
git diff origin/main...HEAD --name-only
```

Expected: 提交切片与 Task 1–10 一一对应，无 dev-stack 文件、密钥、日志、
target 或 `.DS_Store`。

**Step 2: 运行最终门禁**

```bash
mvn test
mvn verify
bash scripts/verify-flyway-source-parity.sh

mvn -pl worthit-gateway dependency:tree \
  -Dincludes=org.springframework:spring-webmvc,\
jakarta.servlet:jakarta.servlet-api,org.apache.tomcat.embed:*,\
org.flywaydb:*,com.mysql:mysql-connector-j

mvn -pl worthit-reminder/worthit-reminder-client dependency:tree \
  -Dincludes=org.springframework.boot:*,org.springdoc:*,\
jakarta.servlet:jakarta.servlet-api,org.apache.tomcat.embed:*

git diff --check
```

Expected:

- 全量测试和 verify 通过；
- Gateway 不出现 MVC/Servlet/Tomcat/Flyway/MySQL；
- Reminder Client 不出现 Boot Starter/springdoc/Servlet/Tomcat；
- 无空白错误。

**Step 3: 输出完成矩阵**

交付必须逐项列出：

- 已实现；
- 已自动验证；
- 已 dev-stack 真实验证；
- 未验证；
- 仍待第一条业务纵切补齐的 `TECH-SEC-001/002/006`；
- 本次启动且仍运行的容器/PID；
- 精确停止命令；
- WorthIt HEAD、dev-stack HEAD 和各自 remote 状态；
- 未推送 WorthIt 的事实。

只有上述证据一致时才声明 Phase 0 基础设施运行时完成。
