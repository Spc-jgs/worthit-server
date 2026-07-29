# Java 代码规范

## 语言与包命名

- 使用 Java 17 已正式支持的语言能力，不使用预览特性。
- 根包名固定为 `com.shaopc.worthit`。
- 包名全小写，按服务、业务子域和层次组织，例如：

```text
com.shaopc.worthit.tracking.item.interfaces
com.shaopc.worthit.tracking.item.application
com.shaopc.worthit.tracking.item.domain
com.shaopc.worthit.tracking.item.infrastructure
```

- 不建立 `util`、`misc`、`manager`、`common` 等含义不清的大杂烩包。
- 类型使用 PascalCase，方法、参数和字段使用 camelCase，常量使用 UPPER_SNAKE_CASE。
- 布尔值使用能直接表达真假语义的名称，例如 `reminderEnabled`、`deleted`；避免 `flag`、`statusFlag`。
- 接口不添加无意义的 `I` 前缀。Application Service 使用 `*Service` 接口与
  `*ServiceImpl` 实现，Controller、Scheduler 和其他调用方只依赖接口。Repository、
  Gateway、Client Adapter 等实现仍按职责命名，例如 `MybatisItemRepository`、
  `WechatLoginGateway`，不机械使用 `Impl`。

## 类型角色与后缀

同一概念在不同边界使用不同类型，不因字段相似而混用。

| 边界 | 推荐命名 | 规则 |
| --- | --- | --- |
| 公网入参 | `CreateItemRequest` | 只表达 HTTP 请求，不进入 Domain |
| 公网出参 | `ItemDetailResponse` | 只表达接口契约，不暴露 DO |
| 应用命令 | `CreateItemCommand` | 表达写用例及服务端生成语义 |
| 应用查询 | `GetItemQuery` | 表达读用例条件 |
| 应用结果 | `ItemResult` | 在 Application 与 Interfaces 之间传递 |
| 内部 Client | `ReconcileReminderCommand` | 由 Client 所有者维护的稳定跨服务契约 |
| 领域对象 | `Item`、`Money` | 承载业务不变量，不带 Web/持久化注解 |
| 持久化对象 | `ItemDO` | 只在 Infrastructure 使用并映射数据库 |

- 禁止使用 `Map<String, Object>`、`JSONObject` 或裸 `Object` 代替稳定契约。
- 公网 DTO、内部 Client DTO、Application 对象、Domain Model 和 Persistence DO 不得跨边界直接复用。
- 不为减少类数量而复用安全边界、生命周期或所有者不同的 DTO。
- 不把完整 Entity/DO 作为 Controller 返回值或 Client 参数。

## 类与依赖

- 优先构造器注入；依赖字段声明为 `private final`。
- 禁止 Spring 字段注入。
- 避免静态可变状态、全局 Service Locator 和隐藏的单例上下文。
- 一个类只承担一个清晰职责；一个公开方法对应一个可描述的用例或能力。
- 方法保持单一抽象层次。复杂条件提取为有业务名称的判断，不堆叠难以解释的布尔表达式。
- 优先不可变对象。稳定的只读契约可在 Jackson、Bean Validation 和框架兼容时使用 `record`。
- Entity、MyBatis DO 或需要受控状态变更的聚合不因追求简短而机械改成 `record`。

## 重复代码与抽象

- 发现重复代码时，先判断它们是否表达相同语义、属于同一所有者并因同一原因变化；代码形状相似不等于应该共用抽象。
- 语义稳定、存在多个真实使用者、依赖边界一致且提取后更容易命名和测试时，应提取到能够拥有该职责的最小范围。
- 提取范围按“类内方法、同模块组件、跨模块 Common”逐级评估，不直接把业务代码下沉 Common。
- 公网 DTO、Client 契约、领域规则和持久化模型即使字段或流程相似，只要所有者、生命周期或安全边界不同，就保持隔离并显式转换。
- 偶然相似、仍在快速变化或提取后需要大量条件分支的代码，可以暂时保留重复，并通过清晰命名和测试控制后续修改风险。
- 只处理当前任务涉及的重复代码；任务范围外的重复只记录，不顺手重构。
- 禁止为了减少代码行数、追求“零重复”或假设未来复用而创建万能工具、过早抽象或新的 Common 模块。

## Lombok

- 新增普通 Java 类时使用 Lombok 消除无业务含义的样板代码，不手写可由稳定 Lombok 注解生成的 getter、setter、构造器、builder 或日志字段。
- Spring Bean 优先使用 `final` 依赖字段配合 `@RequiredArgsConstructor` 实现构造器注入；需要日志字段时使用 `@Slf4j`。
- 按实际职责选用 `@Getter`、`@Setter`、`@RequiredArgsConstructor`、`@Builder`、`@Value`、`@EqualsAndHashCode` 和 `@ToString`，不得为了省事默认给所有类型添加 `@Data`。
- `@Data` 会同时生成 setter、`equals/hashCode` 和 `toString`，不得用于聚合根、Entity、MyBatis DO、安全边界 DTO 或包含敏感字段的类型，除非逐项确认生成行为符合该类型语义。
- 已由 Java `record` 提供访问器、构造器、`equals/hashCode` 和 `toString` 的纯契约，不添加重复的 Lombok 样板注解；枚举、接口和注解类型同样不得添加无效果的 Lombok 注解。
- `@Builder` 只用于参数较多且能提升调用可读性的构造边界，不得绕过领域不变量、Bean Validation 或受控状态转换。
- Lombok 只生成结构性代码。主要方法、重要方法、通用类型、契约字段、常量和枚举仍须遵守本文件的注释与 Javadoc 规则。
- 首次在模块中真实使用 Lombok 时，才在该模块声明依赖；版本继续由根 POM 或现行 BOM 统一管理，不在子模块单独写版本。

## 常量与枚举

- 能被多个调用方复用，或独立表达业务、协议、分页、时间等稳定语义的常量和枚举，应抽到职责单一的专用类型中，不散落在使用类里。
- 禁止建立无明确边界的全局 `Constants`、`Enums` 大杂烩；专用类型应按语义命名并放在其所有者模块。
- 只服务于单个类的实现细节、不会复用且抽离后反而割裂语义的常量，可以保留在类内。
- 重复字面量、稳定状态、固定上限和协议取值不得用“没大碍”为由长期硬编码。
- 生产代码中的常量、枚举类型和每个枚举值都要写注释，说明含义、单位、边界或契约来源。

## 空值与集合

- 方法返回集合时返回空集合，不返回 `null`。
- `Optional` 只用于可能缺失的返回值，不用于字段、方法参数、DTO 或持久化对象。
- 外部入参使用 Bean Validation 和显式业务校验；不要依赖后续 `NullPointerException`。
- 不使用 `null` 表示多个业务状态。使用明确枚举、值对象或结果类型。
- 集合暴露前明确是否允许修改；领域对象优先返回不可变视图或副本。

## 时间、金额、枚举与 ID

- 日期使用 `LocalDate`，无时区的业务时间按契约使用 `LocalDateTime`；禁止新代码使用 `Date`、`Calendar` 或字符串承载时间。
- 涉及时区、存储格式或跨服务时间语义时，以接口和数据库终稿为准，不自行选择系统默认时区。
- 金额和成本使用 `BigDecimal`，构造时避免 `new BigDecimal(double)`。
- 金额计算必须明确精度和舍入方式；不得用 `double` 或 `float` 表达货币。
- 枚举通过终稿冻结的稳定 code 参与接口和持久化，不使用 ordinal。
- Snowflake/`long` ID 在 Java 内部保持强类型语义；跨端 JSON 是否使用字符串严格遵循接口终稿，避免 JavaScript 精度丢失。
- `schemaVersion`、`sourceVersion` 和实体乐观锁 `version` 是不同概念，禁止混用。

## Controller 与应用服务

### Controller

- 只负责协议解析、Bean Validation、身份上下文、调用应用服务和响应转换。
- 不直接调用 Mapper，不书写 SQL，不计算业务状态，不手动控制事务。
- 不信任客户端传入的 userId、TraceId、Same-Token 或仅服务端生成的 `operationType`。

### Swagger / OpenAPI

- Controller 使用 `@Tag` 说明接口域，公开接口方法使用 `@Operation`
  说明用途；请求头、路径参数和容易误解的查询参数按需要使用 `@Parameter`。
- 对业务有意义的 HTTP 状态和统一错误响应使用 `@ApiResponses`，不得虚构
  尚未实现的成功路径、字段、状态或错误码。
- 公网 Request/Response、内部 Client DTO 和公共响应模型使用 `@Schema`
  说明业务含义、单位、格式、可空性和稳定机器值。
- OpenAPI 的人类可读描述使用中文；路径、字段、请求头、错误码和枚举等
  机器契约保持权威文档冻结的英文值。
- `@Schema` 不替代 Bean Validation，Swagger 注释不替代源码 Javadoc。
- 示例不得包含真实手机号、Token、Secret、OpenId 或其他敏感信息。

### Application Service

- 负责编排用例、权限归属、幂等入口、事务和 Outbox。
- 使用 `*Service` 接口声明公开用例，`*ServiceImpl` 承载实现；接口不得复制实现
  细节，Impl 不得退化为空转代理。
- 在边界处完成 Request/Client DTO、Command/Query、Domain Model、DO 之间的转换。
- 明确事务开始和结束，不把远程调用当成本地事务的一部分。
- 状态转换调用 Domain 行为，不在多个 Application Service 中复制同一业务规则。

## 异常与结果

- 业务失败使用稳定业务错误码和明确异常类型，不用异常消息承担机器契约。
- Java 代码中的异常消息、校验提示和其他面向开发者或用户阅读的文本使用中文。
- 错误码、JSON 字段、HTTP 头、数据库字段、包路径、外部协议值等机器契约保持权威文档冻结的原始格式，不为满足中文规范擅自翻译。
- 不捕获 `Exception` 后忽略、返回假成功或只打印日志。
- 只在能够恢复、转换边界语义或补充必要上下文时捕获异常。
- 异常转换保留原始 cause；对外响应不得泄露堆栈、SQL、内部类名或敏感配置。
- 资源归属错误按接口终稿使用 404 防枚举，不擅自改成 403。
- 不用 `boolean` 同时表达成功、失败、幂等、冲突等多个结果；需要时使用具名结果类型。

## 日志

- 使用参数化日志模板，不使用字符串拼接构造日志。
- 日志模板和人工排障信息使用中文；协议值、标识符、状态码和外部系统原文按契约保留。
- 记录能定位问题的业务 ID、事件 ID、目标服务、稳定状态和可信 TraceId。
- 不记录 Token、Secret、密码、完整 openid、手机号、身份证、完整请求体或其他敏感信息。
- 正常业务拒绝不滥用 ERROR；不可恢复系统故障、契约冲突和数据不变量破坏必须有可观测证据。
- 同一异常由最了解处理结果的边界记录一次，避免每层重复打印堆栈。

## 注释与 Javadoc

- 注释解释“为什么”、业务不变量、并发假设和非直观取舍，不逐行复述代码。
- 名称能够表达意图时，不用注释弥补模糊命名。
- 通用类、公共内部 Client 接口、公共契约类型必须写类级 Javadoc，说明职责、适用边界和关键约束。
- 通用方法、主要公开方法和重要私有方法必须写 Javadoc 或必要注释，说明用途、参数、返回值、异常或关键副作用。
- 常量、枚举类型、枚举值、契约字段和稳定错误码必须写必要 Javadoc，不允许仅靠名称猜测边界含义。
- 简单访问器可以使用简洁 Javadoc，但不能因为实现只有一行就省略通用 API 的语义说明。
- 并发、幂等、摘要计算和版本比较代码必须注明对应终稿章节或门禁编号。
- 删除或改变行为时同步更新注释；过时注释视为缺陷。

## 禁止事项

- 魔法数字、魔法字符串和散落的状态 code；
- 超大通用 DTO、双向对象图和跨层循环依赖；
- 通过反射、静态工具或 ThreadLocal 绕过正常依赖边界；
- 在 Domain 中依赖 Spring Web、MyBatis、Client 或数据库实现；
- 为未来扩展预建没有真实使用者的抽象和模块；
- 以“代码更少”为理由牺牲契约所有权、安全边界或可测试性。
