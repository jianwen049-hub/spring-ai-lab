# 02 · Advisor、Tool Calling 与结构化输出

## 当时业务痛点是什么

退款助手第二周，产品加了三条硬需求：

1. **每次调用必须可审计**：谁在什么时候、花了多少 token、有没有触发 tool。
2. **模型不能自由发挥退款**：只能通过名为 `refund_order` 的工具，且参数要过 schema。
3. **风控要结构化结果**：下游规则引擎吃的是 `approve / reason / riskScore`，不是一段散文。

当时代码里是：Controller 调 LLM → 拿到 string → 三个 if 正则 → 调支付 HTTP。结构一变，正则全碎；审计要翻应用日志全文检索「refund」。

## 当时我怎么看这个问题

Spring AI 把这三类能力分别建模成：

- **Advisor**：环绕 ChatClient 的 before/after 管道（很像 HandlerInterceptor / Filter，但面向「一次 LLM 回合」）。
- **Tool Calling**：模型返回结构化 tool_calls，客户端执行后再回灌。
- **Structured Output / BeanOutputConverter**：把模型输出约束成 Bean。

我的判断：业务痛点不是「模型够不够聪明」，而是**缺少围绕一次推理的横切能力与输出契约**。

## 试过什么

1. **AOP 切 RestTemplate**：能记耗时，记不到「第几轮 tool」、也改不了 messages。
2. **把审计写进每个 Service**：复制粘贴，漏写一个就审计缺口。
3. **用 function calling 但自己拼 OpenAI JSON**：字段名 `function_call` / `tool_calls` 随模型版本漂移，维护成本高。
4. **要求模型「只输出 JSON」靠 prompt**：成功率 80%——剩 20% 加 markdown 代码围栏或口头禅，Jackson 直接炸。
5. **Schema 只写在文档里不进运行时**：模型与后端各执一份，联调靠吼。

## 为什么否决

否决「prompt 约束代替协议」。支付与风控必须：

- 横切逻辑（日志、预算、RAG）**可插拔、可排序**，而不是写死在 Facade；
- Tool **有名字、有 schema、有执行器**；
- 结构化输出 **解析失败要显式失败**，不能默默当成拒绝退款。

否决「每个业务自己解析 tool_calls」。那是把模型供应商协议泄漏到全域。

## 最终怎么解

### Advisor 管道

```java
public interface Advisor {
  List<Message> before(List<Message> messages, ChatOptions options);
  ChatOptions beforeOptions(...);
  ChatResponse after(...);
  String name();
}
```

本仓内置：

- `LoggingAdvisor`：只记 messageCount / tokens / 是否 tool，不落 PII 正文。
- `TokenBudgetAdvisor`：超预算先裁非 system 旧消息，再压 maxTokens；after 若仍超则抛错。
- `RagAdvisor`：见下一篇。

ChatClient 按注册顺序执行 before，逆业务习惯上「after 仍按同一列表顺序」——实验室保持简单；生产若要洋葱模型可再改。

### Tool

`ToolCallback`：`name` / `description` / `inputSchemaJson` / `call(args)`。  
`RefundTool` 模拟支付退款副作用，并用 `AtomicInteger` 计数。  
`IdempotentToolGuard` 按 `toolName + orderId` 缓存结果——同一订单第二次 call 直接 replay（04 详写）。

Stub 模型看到「退款」+ 订单号就产出 `ToolCallRequest`；ChatClient tool loop 执行后回灌 `Message.tool(...)`，再问模型。Stub 第二轮返回文本，结束循环。

### 结构化输出

`StructuredOutputParser.parseRefundDecision`：容忍 ```json 围栏，抽取 `approve` / `reason` / `riskScore`。失败抛 `IllegalArgumentException`，由上层决定重试或转人工——**绝不默认为 approve=false 冒充风控拒绝**（那会污染风控报表）。

## 用到的具体 skill / 技巧清单

| Skill | 用法 |
|-------|------|
| **Advisor 横切** | 日志 / 预算 / RAG 与业务 prompt 解耦 |
| **Tool 一等公民** | schema 跟代码走，不跟 wiki 走 |
| **Tool loop 有上限** | 防止模型反复调同一工具刷副作用 |
| **副作用计数器** | 单测证明幂等护栏真的挡住了第二次 |
| **结构化失败显式化** | 解析失败 ≠ 业务拒绝 |
| **去围栏** | 生产也常见模型包 markdown |
| **Logging 最小化** | 默认不落 prompt 全文 |
| **Schema 字符串进仓库** | 联调双方同一份 |

## 代码解析（按文件路径）

### `lab/src/main/java/com/jianwen/springailab/advisor/Advisor.java`

最小接口。Spring AI 正式 API 更丰富（`CallAroundAdvisor` 等）；实验室保留 before/after 心智模型，足够讲清「管道」。

### `lab/src/main/java/com/jianwen/springailab/advisor/LoggingAdvisor.java`

`Entry` 列表供 Smoke 断言。线上应接到 Micrometer / OpenTelemetry span attributes：`gen_ai.usage.input_tokens` 等。

### `lab/src/main/java/com/jianwen/springailab/tool/ToolCallback.java` / `ToolRegistry.java`

注册表用 `LinkedHashMap` 保序，方便把工具列表序列化进 system 或 API `tools` 字段。

### `lab/src/main/java/com/jianwen/springailab/tool/RefundTool.java`

`call` 每次自增 `invocations`，返回带 `refundId` 与 `sideEffectCount` 的 JSON。幂等护栏外再调才会变大。

### `lab/src/main/java/com/jianwen/springailab/structured/StructuredOutputParser.java`

无 Jackson，故意用手工抽取——逼你看见「结构化」在脏输出上的真实工作。上 Spring AI 后换 `BeanOutputConverter<RefundDecision>` 即可，DTO 可原样保留。

## 线上 / 联调还会踩的坑

1. **Advisor 顺序**：RAG 放在 TokenBudget 之后，可能刚注入的上下文被裁掉；预算应在 RAG 之后再跑一轮，或给 RAG 片段预留配额。
2. **Tool 名不一致**：模型被 prompt 写成 `refundOrder`，注册表是 `refund_order`——直接 unknown tool。应用枚举锁死名字。
3. **schema 与实现漂移**：改了 `amountCents` 必填却忘了更新 `inputSchemaJson`，模型漏传，支付接口 400。
4. **结构化「看起来像成功」**：`approve` 拼成 `approved` 时，本仓 parser 会得到 `approve=false`——这是静默默认，生产应改为「缺字段即失败」。
5. **多 tool 并行**：有的模型一次返回多个 tool_calls；执行顺序与事务边界要有策略（支付域通常强制串行）。
6. **把 Tool 结果再给用户看**：退款内部 ID 可能不该出话术；要有一层「对内结果 → 对外话术」的转换。
