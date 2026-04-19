# Portfolio Upgrade Plan

Concrete plan to evolve this repo from a Spring AI tutorial fork into a Staff/Senior-grade GenAI platform portfolio artifact that stands up to engineering interviews.

## Context

- Current repo: Dog Adoption Assistant — Spring Boot + Spring AI + AWS Bedrock (Nova Micro chat, Cohere `embed-english-v3`) + PGVector, with `spring-ai-starter-mcp-client` declared and `QuestionAnswerAdvisor` wired into the prompt chain.
- Target audience for this portfolio: hiring managers for Senior/Staff roles in AI Platform, GenAI Infra, Applied AI, Agentforce, and similar positions.
- Guiding principle: ship the things that answer real interview questions with code and numbers, not vocabulary.

## Pre-work: Resume vs. repo integrity check

Before any new work, verify every claim on the resume bullet matches `main`:

- [ ] `QuestionAnswerAdvisor` is invoked via `.advisors(...)` in every chat path, and retrieval actually influences answers for a query whose correct answer exists only in the seeded `dog` rows.
- [ ] A real MCP server exists (not just the client dep) exposing at least one working tool over SSE that the LLM actually calls end-to-end. If it does not, either build it (Phase 0) or soften the resume bullet until it does.
- [ ] No hardcoded AWS credentials anywhere in the repo (Makefile, `application.yml`, scripts).

---

## Phase 0 — Make every resume claim true (4–6 hours, mandatory)

Goal: the repo is defensible line-by-line against the resume.

- [ ] Confirm `.advisors(questionAnswerAdvisor)` is on the `ChatClient.prompt()` call in `AdoptionsAssistanceController`. Configure `top-k` and `similarity-threshold` via `application.yml`.
- [ ] Verify RAG with a question answerable only from seeded data (e.g., a specific dog name or unique description phrase). Capture the request/response pair in the README.
- [ ] Build a real MCP server module `mcp-server-scheduling/` using `spring-ai-mcp-server-webflux-spring-boot-starter`:
  - [ ] Tool `checkAvailability(date)` returns open slots from an `appointment` table.
  - [ ] Tool `scheduleAppointment(dogId, userEmail, slot)` persists and returns a confirmation.
  - [ ] Exposed over SSE on a separate port; added to `docker-compose.yml`.
- [ ] Wire the main app as the MCP client via `spring.ai.mcp.client.sse.connections.scheduling.url`.
- [ ] Add an integration test that proves the LLM actually invokes `scheduleAppointment` end-to-end (Testcontainers for Postgres+PGVector, WireMock or local Bedrock stub if needed, live server fine for manual runs).
- [ ] Remove hardcoded AWS credentials from the Makefile. Switch to AWS default credential chain; document IAM role / `aws sso` setup in `README.md`.
- [ ] Rewrite `README.md`: one-paragraph summary, architecture diagram (mermaid), prerequisites, `make up` / `make demo` commands, sample `curl` showing a retrieval-grounded answer and a tool-calling flow, and an asciinema/GIF demo link.

Exit criteria: a reviewer who opens the repo cold can run it in under 10 minutes and see both RAG and tool calls working.

---

## Phase 1 — Retrieval quality + evals (1 weekend, highest interview ROI)

Two things senior GenAI interviews consistently probe that nothing here currently demonstrates: (1) do you know your system is correct? (2) do you know how to improve retrieval beyond naive cosine?

### 1a. Golden-set eval harness

- [ ] New module `eval/` with `golden_set.jsonl` of 50–100 curated Q/A pairs. Entry schema: `{id, question, must_include_dog_ids:[...], must_not_include_dog_ids:[...], reference_answer, category}`. Cover factual lookup, multi-dog comparison, out-of-scope refusal, multi-turn follow-ups.
- [ ] `RetrievalEvaluator` runs `vectorStore.similaritySearch(...)` per question and computes recall@{1,3,5}, precision@{1,3,5}, and MRR vs. `must_include_dog_ids`.
- [ ] `ResponseEvaluator` runs end-to-end (RAG + chat) and scores with LLM-as-judge using a *different-family* model to reduce self-bias (e.g., Claude Haiku on Bedrock judging a Nova Micro target). Dimensions: groundedness, answer correctness, refusal correctness.
- [ ] Emit JSON + Markdown report: overall pass rate, per-category breakdown, regressions vs. baseline snapshot under `eval/baselines/`.

### 1b. Retrieval upgrades

- [ ] Hybrid search: Postgres `tsvector` + `ts_rank` BM25 on `dog.description`, fused with vector cosine via Reciprocal Rank Fusion (RRF). Re-run eval, record delta in `EVAL.md`.
- [ ] Reranker: Cohere Rerank v3.5 on Bedrock (or `bge-reranker-base` as a sidecar for cost-control demo). Rerank top-20 to top-3. Re-run eval, record delta.
- [ ] Better chunking: one document per dog with structured `metadata` (breed, age, temperament); test metadata-filtered search. Report recall@k delta.

### 1c. CI

- [ ] `.github/workflows/eval.yml`: on every PR run evals on a dev slice; nightly cron runs full golden set. Fail build if recall@3 drops below threshold. Comment PR with diff vs. main baseline.
- [ ] Standard unit + integration tests using Testcontainers for Postgres+PGVector. No mocks for the vector layer.

Exit criteria: `EVAL.md` contains a baseline scorecard with three retrieval strategies compared on the golden set, and CI enforces no regression.

---

## Phase 2 — Observability, cost, and latency (1 weekend)

Leverage the existing distributed-systems background (OTel Collector, multi-region telemetry) to produce the concrete numbers the resume currently lacks.

- [ ] OpenTelemetry via `opentelemetry-spring-boot-starter`. Enable Spring AI 1.0.x built-in spans for chat, embedding, and vector-search calls. Export OTLP to local Jaeger/Tempo added to `docker-compose.yml`.
- [ ] Custom spans around each MCP tool call with attributes: `tool.name`, `tool.latency_ms`, `tool.success`, `llm.input_tokens`, `llm.output_tokens`, `llm.model`, `llm.cost_usd`.
- [ ] Token + cost accounting: extract `ConverseResponse.usage()`; publish Micrometer meters `llm.tokens.input{model}`, `llm.tokens.output{model}`, `llm.cost.usd{model}`, `rag.retrieval.latency`, `rag.retrieval.recall_at_3` (optional shadow eval on live traffic).
- [ ] Prometheus + Grafana in `docker-compose.yml`. Commit `observability/grafana/dashboards/agent.json` with: p50/p95/p99 end-to-end latency, tokens/req, $/req, tool-call success rate, retrieval top-k hit rate.
- [ ] Replay harness: log every `(trace_id, prompt, retrieved_context, tool_calls, response)` tuple to a `trace` table. Admin endpoint `POST /replay/{traceId}` re-runs a past query against current code.

Exit criteria: Grafana screenshots in `PERF.md` with real numbers (p50/p95/p99 latency, tokens/req, $/query, tool-call success rate).

---

## Phase 3 — Reliability and safety (1 weekend)

- [ ] Resilience4j circuit breaker + retries around Bedrock: exponential backoff with jitter on `ThrottlingException`; fallback chain Nova Pro → Nova Micro → cached → polite failure, gated by a Togglz feature flag.
- [ ] Bounded agent loop: cap tool-call iterations per request (≤5), cap total tokens per request, cap wall-clock per request. Reject prompts over N input tokens. Log and alert when any cap trips.
- [ ] Prompt-injection test suite `security/injection-tests.jsonl`: ~30 patterns (ignore-previous-instructions, tool-hijack via retrieved content, data-exfil via markdown links, Unicode homoglyphs). Assert the agent refuses or sanitizes. Run in CI.
- [ ] Tool allowlist + argument validation: `ToolGuard` component validates JSON schema of args, checks `(user, tool, resource)` permission, enforces dollar/quantity thresholds, writes an immutable record to `audit_log`.
- [ ] Output filtering: regex + Bedrock Guardrails around PII/profanity. Log blocked outputs with reason.
- [ ] `SECURITY.md`: threat model mapped to OWASP LLM Top 10, documenting per-component IAM roles and least-privilege design.

Exit criteria: injection suite green in CI, `SECURITY.md` published, audit log populated during a demo run.

---

## Phase 4 — Load test and produce numbers (half weekend)

- [ ] k6 or Gatling scenario: 50 concurrent users × 5 queries × 10 minutes. Mix: 70% RAG-only, 20% tool-calling, 10% multi-turn.
- [ ] Run matrix: `{Nova Micro, Nova Pro} × {no-rerank, rerank}`. Commit `load/results/` CSVs plus a one-page `PERF.md`: throughput (req/s), p50/p95/p99 latency, error rate, $/1k queries, tokens/sec.
- [ ] Retrieval-quality-under-load: verify p95 recall@3 holds under concurrency.

Exit criteria: `PERF.md` contains real numbers ready to drop into the resume.

---

## Phase 5 — Optional differentiator: explicit planner (1 weekend)

- [ ] Second controller path `/plan-execute` implementing plan-then-execute (either a small in-house planner or a LangGraph4j port).
- [ ] Compare reactive (ReAct) vs. plan-and-execute on the golden set: which query classes does each win on? Record in `EVAL.md`.

Exit criteria: data-backed answer to "ReAct vs. plan-and-execute" rather than vocabulary.

---

## Repo hygiene (in parallel with all phases)

- [ ] Rename repo to a more descriptive slug (e.g. `spring-ai-agent-reference` or `agentic-rag-dogadopt`).
- [ ] Docs: `README.md`, `ARCHITECTURE.md`, `EVAL.md`, `SECURITY.md`, `PERF.md`, `CHANGELOG.md`.
- [ ] Badges: CI status, eval pass rate, coverage.
- [ ] Tag `v1.0.0` after Phase 0+1 complete.
- [ ] Enable Dependabot; add `CODEOWNERS`.
- [ ] Switch to semantic commits (`feat:`, `fix:`, `perf:`, `test:`, `docs:`).

---

## Target resume bullet (replace after Phase 2, strengthen after Phase 4)

```
● Built Java/Spring Boot agentic RAG service on AWS Bedrock (Nova Micro chat, Cohere embed-english-v3) with PGVector
  hybrid retrieval (BM25 + cosine, RRF-fused, Cohere Rerank v3.5), achieving recall@3 of 0.94 on a 100-Q golden set
  (+31% over naive cosine) at p95 820ms and $0.0021/query.
● Designed companion MCP server (Spring AI MCP, SSE transport) exposing schedule/availability tools with JSON-schema
  argument validation, per-user permission scoping, and immutable audit log; sustained 120 req/s at 99.2% tool-call
  success with Resilience4j circuit breakers, bounded agent loops, and Bedrock Guardrails.
● Built eval harness (retrieval metrics + LLM-as-judge groundedness/correctness) wired into GitHub Actions to block
  regressions; instrumented full pipeline with OpenTelemetry → Prometheus/Grafana for per-request token, cost, latency,
  and retrieval-quality telemetry; added prompt-injection test suite covering OWASP LLM Top 10.
```

Numbers above are placeholders — replace with measured values from Phase 1/2/4.

---

## Execution schedule

| Weekend | Phases | Outcome |
|---|---|---|
| 1 | Phase 0 + Phase 1a | Repo matches resume; baseline eval scorecard exists |
| 2 | Phase 1b/1c + Phase 2 | Hybrid + rerank with CI gating; full observability stack |
| 3 | Phase 3 + Phase 4 | Safety suite in CI; real load-test numbers |
| 4 (optional) | Phase 5 + polish | Planner comparison; resume rewrite; `v1.0.0` release |

Stopping after Weekend 2 already produces a materially stronger artifact than 95% of candidates who list "built an agentic AI project" on their resume. Weekend 3 is where the repo becomes genuinely senior-grade.
