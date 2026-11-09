# 01 · RestTemplate 硬调 LLM 为什么会在业务里炸掉

## 当时业务痛点是什么

支付与客服中台要上一个「智能退款助手」：运营同学粘贴用户原话，系统判断是否可退、是否要走风控复核、并给出话术。第一版没有 Spring AI，也没有统一客户端——各个微服务直接 `RestTemplate` / `WebClient` 拼 OpenAI 兼容 JSON。

现场现象很快出来：

1. **超时不可控**：有的调用 5s，有的 60s；网关 30s 先断，上游模型还在算，对账线程却以为失败。
2. **重试无契约**：HTTP 502 一重试，模型侧可能已经产生了一次「建议退款」的 tool 倾向；业务侧又发起一次，出现**重复退款意向**（当时还没真正打支付，但工单重复刷）。
3. **Prompt 散落**：system prompt 复制粘贴在 4 个 Service；改一句合规措辞要发四个包。
4. **没有 Tool 契约**：模型自由文本里夹杂「请调用 refund」，下游用正则捞，捞不到就人工。
5. **可观测性极差**：日志只有整段 response body，无法按「哪次 advisor / 哪次 tool」切片做事后复盘。

## 当时我怎么看这个问题

第一反应是「再包一层 HttpClient，把 timeout / retry 写进公共库」。这能缓解 1 和 2 的表象，但解决不了 **对话状态、工具协议、结构化输出、上下文裁剪** 这些「AI 特有」的问题。HTTP 客户端再优雅，它仍然把 LLM 当普通 REST 资源；而 LLM 调用是**有状态、有副作用倾向、有 token 成本**的对话过程。

## 试过什么

1. **公共 RestTemplate + 统一 Timeout**：超时整齐了，Prompt 与 Tool 依旧散。
2. **自研 `LlmFacade.call(String prompt)`**：所有人塞超长 string，无法区分 system/user/tool。
3. **在 Facade 里硬编码 JSON schema 解析**：每个业务 DTO 写一套 Jackson 注解 + 失败重试，和模型输出漂移打架。
4. **直接上 Spring AI 全量 starter + 真实 Key**：本地与 CI 强依赖外网与配额；博客读者与离线联调挂掉。

## 为什么否决

否决「只包 HTTP」。支付域要的是：

- **消息角色清晰**（system / user / assistant / tool）
- **可插拔拦截**（日志、RAG、token 预算）——对应 Advisor
- **Tool 一等公民**（名字、schema、执行、回灌）
- **离线可测**（Stub 模型），线上再换真实 ChatModel

否决「一上来绑死真实 Spring AI 依赖做实验室」——读者没有 Key、CI 没有配额时，仓库是死的。本仓用 **纯 JDK 17** 复刻 ChatClient / ChatModel 形状，接口语义对齐 Spring AI，方便以后把 `StubChatModel` 换成 `OpenAiChatModel`。

## 最终怎么解

核心抽象：

```
ChatClient
  ├─ List<Advisor>          // before/after 管道
  ├─ ChatModel              // StubChatModel | 未来真实模型
  └─ ToolRegistry           // 可选 tool loop
```

一次 `prompt`：

1. Advisors `before` 改写 messages / options（RAG 注入、裁剪 token）
2. `ChatModel.call`
3. 若有 `ToolCallRequest`，执行 Tool，把结果以 `Role.TOOL` 回灌，再 call（有 `maxToolRounds`）
4. Advisors `after`（日志、预算校验）

离线规则在 `StubChatModel`：用户话里同时出现「退款」与 `ORD-` → 发出 `refund_order` tool call；出现「结构化」→ 返回 JSON；`STREAM_DEMO` → 长文本供 SSE。

## 用到的具体 skill / 技巧清单

| Skill | 用法 |
|-------|------|
| **ChatModel 抽象** | 业务只依赖接口，Stub / 真模型可替换 |
| **Message 四角色** | 禁止把 tool 结果塞进 user 字符串 |
| **ChatClient 门面** | 统一入口，避免 RestTemplate 散落 |
| **Tool loop 上限** | `maxToolRounds` 防止模型死循环调工具 |
| **离线确定性 Stub** | 关键词触发，CI 无 Key 可绿 |
| **Token 粗估** | CJK 按字、拉丁按 4 char≈1 token，够做预算闸门 |
| **Options 显式化** | timeout / maxTokens / temperature 进对象，不进魔法常量 |

## 代码解析（按文件路径）

### `lab/src/main/java/com/jianwen/springailab/chat/Message.java`

`Role` 枚举 + 工厂方法 `system/user/assistant/tool`。Tool 消息带 `name`，方便日志与回放对齐 Spring AI 的 tool 消息结构。

### `lab/src/main/java/com/jianwen/springailab/chat/ChatModel.java`

`call` + 默认 `stream`（把完整答案切成几段喂给 Consumer）。真实 Spring AI 的流式是 token 级；实验室用分块模拟前端 EventSource 行为。

### `lab/src/main/java/com/jianwen/springailab/chat/StubChatModel.java`

无网络。`estimateTokens` 供 `TokenBudgetAdvisor` 复用。订单号提取对 `ord-` 大小写不敏感，回写时统一大写，避免幂等键抖动。

### `lab/src/main/java/com/jianwen/springailab/chat/ChatClient.java`

Builder 风格对齐 Spring AI `ChatClient.builder(chatModel).defaultAdvisors(...).build()`。`prompt` 内含 tool loop；`streamPrompt` 走 `ChatModel.stream`。

### `lab/src/main/java/com/jianwen/springailab/SmokeMain.java`

串起 echo / 结构化 / tool 幂等 / SSE / RAG / 日志六条断言，最后打印 `smoke_check OK`。

### `lab/scripts/smoke_check.sh`

`find` 全部 `.java` → `javac` → `java SmokeMain`。不依赖 Maven 仓库，离线可跑。

## 线上 / 联调还会踩的坑

1. **网关超时 < 模型超时**：ChatOptions.timeoutMs 必须与 Ingress / 网关对齐，否则会出现「客户端已断、上游仍计费」。
2. **重试与 Tool 副作用**：HTTP 层重试不能包住「已执行 refund」的回合；幂等要做在 Tool 层（见 04）。
3. **Prompt 漂移**：没有统一 ChatClient 时，A 服务 system 已改合规口径，B 服务还在旧文案——审计时两套话术对不上。
4. **把整段 JSON 当 user 消息**：模型会学着「复述 JSON」而不是「调用 tool」；角色一定要分开。
5. **Stub 与真模型行为差**：上线前用同一组黄金问题集对比 Stub 触发路径 vs 真模型 tool_calls 字段，避免只绿了实验室。
6. **日志打全文**：支付单号、手机号进 prompt 后原样落盘会有合规问题；LoggingAdvisor 默认只记计数与 token，不记正文。
