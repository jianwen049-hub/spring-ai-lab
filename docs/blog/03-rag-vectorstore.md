# 03 · VectorStore 抽象 vs 手工拼装 RAG

## 当时业务痛点是什么

退款助手上线后，幻觉集中在三类知识：

- **支付 SLA**：「退款多久到账」——模型有时说即时，有时说七个工作日。
- **风控拒绝码**：把 R-403 解释成「余额不足」。
- **运营窗口**：夜间批处理导致查询延迟，模型不知道，反而指责用户。

运营把 FAQ 丢进一个巨型 system prompt。结果：token 账单飙升、稍长会话就截断、改一条 FAQ 要发版。

## 当时我怎么看这个问题

RAG 的本质不是「上向量数据库」，而是：

1. **检索**：按问题找回相关片段；
2. **注入**：把片段放进模型可见上下文，并约束「只可引用」；
3. **可替换存储**：内存 / PGVector / Elasticsearch 换后端，业务代码不改。

Spring AI 的 `VectorStore` 正是为此。手工拼装常见于早期：自己 embedding、自己 top-k、自己拼 prompt——能跑，但每个项目复制一份。

## 试过什么

1. **全量 FAQ 塞 system**：短平快，上下文爆炸，无法按租户隔离。
2. **关键词 SQL `LIKE %退款%`**：中文同义与拒识码效果差。
3. **直接调 Embedding API + 自建 cosine**：可以，但实验室与 CI 强依赖 Key；且业务代码绑死某一家 embedding。
4. **把「检索」写在 Controller**：Service 层无法复用，Advisor 也插不进统一管道。

## 为什么否决

否决「巨型 system prompt 当知识库」——这是把内容运营做成了发版运维。  
否决「业务代码直接 new 某云向量库客户端」——换存储或混合检索时要改业务。  
保留「可解释的离线检索」用于实验室：本仓 `InMemoryVectorStore` 用 token Jaccard 近似相似度，**接口形状**对齐 `similaritySearch(query, topK)`，以后换成真实 embedding 实现即可。

## 最终怎么解

```
InMemoryVectorStore.demo()  // 种子：支付 / 风控 / 运营 FAQ
        │
        ▼
   RagAdvisor(store, topK)
        │  before(): 取最后一条 user → search → 前置 system「检索上下文…」
        ▼
   ChatClient.prompt(...)
```

种子文档示例：

- `p1` 退款 T+1 / 大额复核  
- `p2` 订单号 `ORD-YYYY-序号`  
- `r1` 风控 R-403 设备指纹  
- `o1` 02:00-04:00 批处理窗口  

`RagAdvisor` 明确要求模型「只可引用以下片段，勿编造」——这不能杜绝幻觉，但把**可追责的证据**留在 messages 里，出事故时能回放。

## 用到的具体 skill / 技巧清单

| Skill | 用法 |
|-------|------|
| **VectorStore 接口** | add + similaritySearch，后端可换 |
| **Advisor 注入 RAG** | 业务 prompt 无感知 |
| **「只可引用」约束** | 降低瞎编概率，利于追责 |
| **离线玩具相似度** | Jaccard over 中英 token，无 Key |
| **元数据 domain** | 为后续按域过滤留钩子 |
| **topK 显式** | 避免一次塞太多噪声 |
| **与 TokenBudget 协作** | 注入后仍可能被裁，顺序要设计 |

## 代码解析（按文件路径）

### `lab/src/main/java/com/jianwen/springailab/rag/Document.java`

`id` + `text` + `metadata`。生产还会有 embedding 字段或外置向量；实验室检索瞬时算分，不存向量。

### `lab/src/main/java/com/jianwen/springailab/rag/VectorStore.java`

故意保持两方法。Spring AI 还有 `delete`、过滤器等；博客读者先掌握最小闭环。

### `lab/src/main/java/com/jianwen/springailab/rag/InMemoryVectorStore.java`

分词：汉字 unigram/bigram + 英文词。`similaritySearch` 按 Jaccard 降序，过滤 0 分。`demo()` 填充四条跨域 FAQ。

### `lab/src/main/java/com/jianwen/springailab/advisor/RagAdvisor.java`

从消息尾部找最后一条 USER 作 query；命中则 **前置** system（不是追加在尾部），让模型先看见证据再看问题——实践里比「用户问题后再贴资料」更稳一点。

## 线上 / 联调还会踩的坑

1. **检索空结果仍强答**：应降级「未命中知识库，转人工」，而不是让模型凭先验瞎说 SLA。
2. **topK 过大**：噪声片段诱导模型拼凑错误政策。
3. **租户隔离**：多商户 FAQ 混库会串政策；metadata filter 必做。
4. **文档更新 vs 索引滞后**：运营改了到账文案，向量未重建，助手仍念旧 SLA——要有索引版本号。
5. **把整页 HTML 当 Document**：噪声极大；先切块、去导航、保留标题路径。
6. **混合检索**：订单号、拒绝码更吃稀疏检索——可与本组织 `hybrid-retrieval` 仓思路对接，VectorStore 前加一路 BM25。
7. **Advisor 与多轮**：第二轮 user 追问「那大额呢？」若只检索最后一句，可能丢掉「退款」主题；应用改写 query 或带上对话摘要。
