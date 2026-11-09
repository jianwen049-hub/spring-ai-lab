# 技术博客（中文深拆）

围绕 **Spring AI 在支付 / 风控 / 运营后端的真实落地**：从 RestTemplate 硬调到 ChatClient，再到 Advisor、Tool 幂等、RAG 抽象、SSE 与 Token 预算。

| # | 标题 | 代码锚点 |
|---|------|----------|
| [01](./01-resttemplate-pain-to-chatclient.md) | RestTemplate 硬调 LLM 为什么会在业务里炸掉 | `chat/ChatModel.java` · `ChatClient.java` · `StubChatModel.java` |
| [02](./02-advisors-tools-structured.md) | Advisor、Tool Calling 与结构化输出 | `advisor/*` · `tool/*` · `structured/*` |
| [03](./03-rag-vectorstore.md) | VectorStore 抽象 vs 手工拼装 RAG | `rag/*` · `advisor/RagAdvisor.java` |
| [04](./04-sse-token-idempotent.md) | SSE 流式、Token 预算与支付域 Tool 幂等 | `stream/*` · `TokenBudgetAdvisor` · `IdempotentToolGuard` |
