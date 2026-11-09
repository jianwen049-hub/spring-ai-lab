# lab — Spring AI shaped offline Java module

Pure **JDK 17** sources that mirror Spring AI concepts:

| Package | Responsibility |
|---------|----------------|
| `chat` | `ChatModel` / `StubChatModel` / `ChatClient` / messages |
| `advisor` | Logging · TokenBudget · Rag advisors |
| `tool` | `ToolCallback` · `RefundTool` · `IdempotentToolGuard` |
| `rag` | `VectorStore` · `InMemoryVectorStore` |
| `structured` | `RefundDecision` parser |
| `stream` | SSE frame formatter |

## Smoke (no Maven / no API key)

```bash
bash scripts/smoke_check.sh
```

Requires `javac` + `java` 17+ on `PATH`.
