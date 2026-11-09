# spring-ai-lab — Spring AI for Real Java Backends

> Open lab on **Spring AI-shaped** chat clients, advisors, structured output, tool calling, VectorStore RAG, SSE streaming, token budgets, and **idempotent tool side-effects** for payment-like flows. Offline-first: pure Java 17 stubs (no API key required). Maven POM included for when you wire a real model.

**Author:** 马健文 / Evans Owen ([jianwen049-hub](https://github.com/jianwen049-hub))

| | |
|---|---|
| **Lab** | [`lab/`](./lab/) — ChatClient · Advisors · Tools · RAG · SSE · smoke |
| **Blog** | [`docs/blog/`](./docs/blog/) |
| **Bio** | [`PROFILE.md`](./PROFILE.md) |
| **Env** | [`.env.example`](./.env.example) |

## 中文速览

团队卡在「RestTemplate 硬调 LLM」时，痛点往往不是模型智商，而是：**超时与重试没有契约、Prompt 散落在各个 Service、Tool 没有幂等、流式与结构化输出各写一套**。本仓用可编译的纯 Java stub 把 Spring AI 风格的 ChatClient / Advisor / Tool / VectorStore / SSE 链路跑通，博客按业务现场写清取舍与踩坑。

## Reading guide (EN)

```bash
# Need JDK 17+ on PATH (lab is offline — no Maven deps required for smoke)
cd lab
bash scripts/smoke_check.sh
# or:
javac -d out $(find src/main/java -name '*.java')
java -cp out com.jianwen.springailab.SmokeMain
```

Optional: copy `.env.example` → `.env` when you later swap `StubChatModel` for a real HTTP model client.

## License

MIT — see [`LICENSE`](./LICENSE).
