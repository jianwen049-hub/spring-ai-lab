# 04 · SSE 流式、Token 预算与支付域 Tool 幂等

## 当时业务痛点是什么

三个互不相关却总在同一周爆的问题：

1. **前端白屏等待**：退款助手有时 8–20s 才返回；产品要「边想边出字」，类似 ChatGPT 的打字效果。
2. **账单惊吓**：有人把几十轮客服通记录进 prompt，单次上万 token；再加 RAG，月成本失控。
3. **退款 dual-submit**：用户连点、网关重试、模型 tool loop 重入，支付侧收到两次退款请求——资损预案被拉响（灰度金额小，但流程是真的）。

## 当时我怎么看这个问题

这不是三个需求，而是同一类工程成熟度问题：

- 流式 = **传输契约**（SSE / WebSocket）与 **模型流式 API** 对齐；
- Token 预算 = **成本与上下文的硬闸门**，不能靠同学自觉；
- Tool 幂等 = **副作用边界**，必须在执行器层，而不是「请模型不要调用两次」。

## 试过什么

1. **前端轮询「任务结果表」**：能做进度条，但体验仍是块状；且要多一套任务状态机。
2. **WebSocket 长连接**：移动端与部分网关不友好；SSE 对「服务器推文字」更轻。
3. **只在网关做 rate limit**：限的是 QPS，不是 token；一次超长 prompt 照样穿。
4. **支付接口靠订单号唯一约束**：正确且必要，但 AI 层若无幂等，仍会放大锁定冲突与错误日志，且用户看见两次「已提交」。
5. **prompt 写「请务必只调用一次 refund」**：模型不背责；重试与双 Tab 更不吃 prompt。

## 为什么否决

否决「只靠支付 DB 唯一键」。那是最后一道闸，不是唯一一道。AI 编排层必须能说清：**同一次业务意图下，工具副作用只发生一次**，并且可回放。

否决「无预算的 ChatClient」。会在沉默中烧钱，直到财务找上门。

否决「为了流式把整个 Spring MVC 绑进实验室」。本仓 `SseStreamer` 只负责 **SSE 帧格式**，不绑 Servlet——教的是帧契约；进 Spring 时用 `SseEmitter` 写出同样的 `event:` / `data:` 即可。

## 最终怎么解

### SSE

```
event: token
data: 支付单状态查询已受理；

event: token
data: 风控规则引擎返回通过；

event: done
data: {"tokens":42}
```

`ChatClient.streamPrompt` → `ChatModel.stream` 分块 → `SseStreamer.chunkSink()`；完成时 `complete(finalJson)`。前端 `EventSource` 听 `token` 拼 DOM，听 `done` 收尾，听 `error` 提示。

### Token 预算

`TokenBudgetAdvisor(4096)`：

1. `before`：估算 messages，超预算则从最早的非 system 消息开始丢；
2. `beforeOptions`：把 `maxTokens` 压到「预算 - 已用」；
3. `after`：若响应总 token 仍超（极端），抛 `IllegalStateException`——宁可使调用失败，也不静默超支。

粗估即可：闸门的意义是**有牙**，不是 tokenizer 级精确。

### 幂等 Tool

`IdempotentToolGuard` 包装 `RefundTool`：

- 键：`refund_order` + `orderId`（从 args JSON 抽出）；
- `putIfAbsent`：先到的执行副作用并缓存，后到的直接返回缓存 JSON；
- Smoke 断言：连续两次 call，`RefundTool.invocations() == 1`，且两次返回字符串相等。

支付域还可把键扩展为 `orderId + amountCents + reason` 的哈希，防止「同单不同金额」被误翻成旧结果——那是产品规则，实验室先钉 orderId。

## 用到的具体 skill / 技巧清单

| Skill | 用法 |
|-------|------|
| **SSE event 分型** | token / done / error，前端好处理 |
| **data 换行转义** | 防 SSE 帧被拆坏 |
| **流式与同步同一 ChatClient** | 行为一致，只是传输不同 |
| **Token 硬预算** | 裁上下文 + 压 maxTokens + after 校验 |
| **保留 system** | 裁剪时不删 system，防人设丢失 |
| **幂等键设计** | toolName + 业务主键 |
| **putIfAbsent** | 并发双请求不双花 |
| **副作用计数可测** | 用 AtomicInteger 证明护栏 |
| **网关重试友好** | 重放返回同一 refundId |

## 代码解析（按文件路径）

### `lab/src/main/java/com/jianwen/springailab/stream/SseStreamer.java`

`formatEvent` 生成标准 SSE 文本。`asBody()` 便于单测与 Smoke 断言包含 `event: token`。

### `lab/src/main/java/com/jianwen/springailab/advisor/TokenBudgetAdvisor.java`

与 `StubChatModel.estimateTokens` 共用粗估。生产可换 tiktoken / 厂商 usage，但 Advisor 边界不变。

### `lab/src/main/java/com/jianwen/springailab/tool/IdempotentToolGuard.java`

`extract` 是迷你 JSON 字段抽取——实验室零依赖；生产用 Jackson 并校验 schema。`asTool()` 返回包装后的 `ToolCallback`，对 ChatClient 透明。

### `lab/src/main/java/com/jianwen/springailab/SmokeMain.java`

第四节专门跑 stream + 幂等双调用；失败即非零退出，保证 CI 有牙。

## 线上 / 联调还会踩的坑

1. **反向代理缓冲 SSE**：Nginx `proxy_buffering on` 会让「打字效果」变成一次性倾倒；需 `X-Accel-Buffering: no` 等配置。
2. **SSE 与鉴权**：`EventSource` 不能自定义 Header 的老问题——要用 cookie 会话或 query token，注意泄漏。
3. **done 事件丢失**：客户端重连可能重复消费；done 里带 `idempotencyKey` / `replyId`。
4. **预算过小裁掉 RAG**：表现为「有知识库却总说不知道」——看 LoggingAdvisor 的 messageCount 是否异常偏低。
5. **幂等键缺字段**：args 无 orderId 时 fallback 到整串 hash——同义 JSON 字段顺序不同会击穿缓存；生产应 schema 校验失败直接拒。
6. **缓存无 TTL**：进程内 ConcurrentHashMap 重启即空；生产用 Redis，并与支付退款单状态机对齐。
7. **把流式当取消语义**：用户点停止，只是前端停渲染；若 tool 已执行，后端不会自动回滚——停止按钮不能等于「撤销退款」。
8. **usage 与粗估不一致**：账单以厂商 usage 为准；粗估只做闸门，对账另接 meter。

## 收束

Spring AI 真正帮到业务的，不是多一个 starter，而是逼你把 **客户端、横切、工具、检索、流式、预算、幂等** 做成可替换的边界。本仓用纯 JDK 把这些边界跑绿；等有 Key 与 BOM 时，把 `StubChatModel` 换成真实 `ChatModel`，博客里的取舍仍然成立。
