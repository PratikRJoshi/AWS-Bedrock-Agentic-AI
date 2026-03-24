# AWS Bedrock Agentic AI — Complete Project Deep Dive

---

# 1. What the Project Does (Deep Dive)

## The Big Picture

This is a **Dog Adoption AI Assistant** — a Spring Boot microservice that lets users ask natural-language questions about adopting dogs, and it answers using a combination of **real data from a database** and a **large language model on AWS Bedrock**. The pattern it uses is called **RAG (Retrieval-Augmented Generation)**.

Think of it like this analogy: imagine you're a customer service rep at "Pooch Palace" (a dog adoption agency). You have a binder of all the dogs available. When someone asks "do you have a friendly dog good with kids?", you first **flip through your binder** (that's the Retrieval part), pull out the relevant pages, then **use your own language skills** to craft a helpful answer (that's the Generation part). This project automates that exact workflow.

## How the Architecture Flows — Step by Step

### Step A: Startup (Data Ingestion + Embedding)

When the app boots up, `ChatClientConfig` does something critical:

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder,
                             DogRepository dogRepository,
                             VectorStore vectorStore) {

    dogRepository.findAll().forEach(dog -> {
        var document = new Document("id: %s\n name: %s\n description: %s".formatted(
            dog.id(), dog.name(), dog.description()
        ));

        vectorStore.add(List.of(document));
    });
```

1. It reads **every dog** from PostgreSQL via `DogRepository.findAll()`
2. For each dog, it creates a `Document` (Spring AI's abstraction for a chunk of text)
3. It calls `vectorStore.add()` — this is where the magic happens. Under the hood:
   - The text is sent to **Cohere's `embed-english-v3`** model on AWS Bedrock
   - Cohere turns the text into a **vector** (a list of ~1024 floating-point numbers) that captures the *semantic meaning*
   - That vector is stored in the **PGVector** table inside PostgreSQL alongside the original text

### Step B: User Asks a Question

When a user hits either endpoint, the controller handles it:

```java
@GetMapping("/{user}/inquire")
public String inquire(@PathVariable("user") String user,
                        @RequestParam String question) {
    List<Message> messages = this.conversationHistory.computeIfAbsent(
        user, k -> new ArrayList<>()
    );

    UserMessage userMessage = new UserMessage(question);
    messages.add(userMessage);

    String response = this.chatClient
        .prompt()
        .messages(messages)
        .options(ChatOptions.builder()
                .model(modelId)
                .build())
        .call()
        .content();

    AssistantMessage assistantMessage = new AssistantMessage(response);
    messages.add(assistantMessage);

    return response;
}
```

1. The user's question is added to their **conversation history** (stored per-user in a `ConcurrentHashMap`)
2. The full history is passed to the `ChatClient`, which sends it to **Amazon Nova Micro** via the Bedrock Converse API
3. The AI generates a response *in-character* as the Pooch Palace assistant (because of the system prompt)
4. The response is stored in history (enabling multi-turn conversations) and returned

### Step C: The System Prompt Sets the Personality

```java
String systemPrompt = """
    You are an AI powered assistant to help people adopt a dog from the adoption\s
    agency named Pooch Palace with locations in Atlanta, Antwerp, Seoul, Tokyo, Singapore, Paris,\s
    Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs available\s
    will be presented below. If there is no information, then return a polite response suggesting we\s
    don't have any dogs available.
    
    If the response involves a timestamp, be sure to convert it to something human-readable.
    
    Do _not_ include any indication of what you're thinking. Nothing should be sent to the client between <thinking> tags.
    Just give the answer.
    """;
```

This constrains the LLM to behave as a dog adoption assistant, not a general-purpose chatbot.

### Infrastructure

The database runs in Docker via `docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:14
    container_name: dog-adoption-db
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: adoptions
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - ./src/main/resources/static/sql:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
```

The `schema.sql` is mounted into `/docker-entrypoint-initdb.d`, so PostgreSQL auto-executes it on first startup — creating the `vector` extension, the `dog` table, and seeding 10 sample dogs.

---

# 2. All Libraries Used

Here's a complete breakdown from `pom.xml`:

### Spring Boot Core Libraries

| Dependency | What It Does |
|---|---|
| **`spring-boot-starter-parent` 3.5.6** | Parent POM — manages versions of all Spring dependencies so you don't have to |
| **`spring-boot-starter-web`** | Provides Spring MVC (DispatcherServlet, embedded Tomcat, REST annotations like `@GetMapping`, `@PostMapping`, `@RequestBody`) |
| **`spring-boot-starter-data-jdbc`** | Lightweight database access — maps Java records/classes to SQL tables without the full JPA/Hibernate overhead. Provides `CrudRepository` |
| **`spring-boot-devtools`** | Hot-reload during development (auto-restarts when code changes) |
| **`spring-boot-starter-test`** | JUnit 5, Mockito, Spring Test context for integration testing |

### Spring AI Libraries (all managed by `spring-ai-bom` version **1.0.3**)

| Dependency | What It Does |
|---|---|
| **`spring-ai-starter-model-bedrock`** | Base AWS Bedrock integration — configures AWS SDK clients, authentication, region settings |
| **`spring-ai-starter-model-bedrock-converse`** | Adds the Bedrock **Converse API** specifically — this is the newer, unified API for chat models (like Nova, Claude, etc.) rather than the older per-model invoke APIs |
| **`spring-ai-starter-vector-store-pgvector`** | Auto-configures a `VectorStore` bean backed by PGVector. Handles creating the `vector_store` table, storing/retrieving embeddings |
| **`spring-ai-advisors-vector-store`** | Provides the `QuestionAnswerAdvisor` class — an "advisor" that intercepts chat prompts, runs a vector similarity search, and augments the prompt with relevant documents (RAG) |
| **`spring-ai-starter-mcp-client`** | Model Context Protocol (MCP) client — enables tool-calling capabilities where the LLM can invoke external tools/functions via the MCP standard over SSE (Server-Sent Events) |

### Transitive/Implicit Libraries (pulled in by the above)

| Library | Pulled In By |
|---|---|
| **AWS SDK v2** (STS, Bedrock Runtime) | `spring-ai-starter-model-bedrock` |
| **PostgreSQL JDBC Driver** | `spring-ai-starter-vector-store-pgvector` |
| **Embedded Tomcat** | `spring-boot-starter-web` |
| **Jackson (JSON)** | `spring-boot-starter-web` |
| **Spring JDBC / DataSource** | `spring-boot-starter-data-jdbc` |
| **SLF4J + Logback** | `spring-boot-starter-*` |

### Infrastructure Libraries (Not in pom.xml but in docker-compose)

| Tool | Version | Purpose |
|---|---|---|
| **PostgreSQL** | 14 | Relational database + PGVector extension host |
| **PGVector extension** | (bundled with Postgres 14 image) | Adds the `vector` column type and similarity search operators to PostgreSQL |
| **Docker Compose** | (system tool) | Container orchestration for the database |
| **Maven Wrapper** | (bundled `mvnw`) | Ensures consistent Maven version across environments |

---

# 3. Deep Dive: PGVector, MCP, and Spring Libraries

## PGVector & the Vector Database

### What is PGVector?

PGVector is a **PostgreSQL extension** that adds a new column type called `vector` and operators for **similarity search** (cosine similarity, L2 distance, inner product). It turns your regular PostgreSQL into a vector database without needing a separate system like Pinecone or Weaviate.

### How it's used in this project:

**Step 1 — Enable the extension** (in `schema.sql`):

```sql
-- Enable pgvector extension for vector similarity search
CREATE EXTENSION IF NOT EXISTS vector;
```

**Step 2 — Spring AI auto-creates a `vector_store` table** when the app starts (handled by `spring-ai-starter-vector-store-pgvector`). This table has columns like:
- `id` (UUID)
- `content` (the original text)
- `metadata` (JSON)
- `embedding` (the `vector` type column — stores the 1024-dimensional float array)

**Step 3 — Documents are embedded and stored** (in `ChatClientConfig`):

```java
dogRepository.findAll().forEach(dog -> {
    var document = new Document("id: %s\n name: %s\n description: %s".formatted(
        dog.id(), dog.name(), dog.description()
    ));

    vectorStore.add(List.of(document));
});
```

When `vectorStore.add()` is called, Spring AI:
1. Sends the document text to **Cohere embed-english-v3** on Bedrock
2. Gets back a vector (array of floats)
3. Stores the text + vector in the `vector_store` table using an `INSERT` with the `vector` type

**Step 4 — Similarity search at query time** (via `QuestionAnswerAdvisor`):

```java
QuestionAnswerAdvisor questionAnswerAdvisor;

AdoptionsAssistanceController(ChatClient chatClient,
                               @Value("${spring.ai.bedrock.converse.chat.model}") String modelId,
                               VectorStore vectorStore) {
    this.chatClient = chatClient;
    this.modelId = modelId;
    this.conversationHistory = new ConcurrentHashMap<>();
    this.questionAnswerAdvisor = new QuestionAnswerAdvisor(vectorStore);
}
```

The `QuestionAnswerAdvisor` is instantiated but **note: it's not yet wired into the `.prompt()` call chain**. When fully wired (e.g., via `.advisors(questionAnswerAdvisor)` on the prompt), it would:
1. Take the user's question
2. Embed it using Cohere
3. Run a `SELECT ... ORDER BY embedding <=> $query_vector LIMIT k` query (cosine similarity)
4. Retrieve the top-K most relevant dog documents
5. Inject them into the prompt context before sending to the LLM

This is the **Retrieval** in RAG — it grounds the LLM's response in actual data.

## Model Context Protocol (MCP) Client

### What is MCP?

MCP (Model Context Protocol) is an **open standard** (created by Anthropic) that defines how LLMs can discover and invoke external tools. Think of it as a "USB-C for AI tools" — a universal interface.

### How it's declared in this project:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

The `spring-ai-starter-mcp-client` dependency enables your Spring Boot app to act as an **MCP client**. This means:

1. **Tool Discovery**: The app can connect to remote MCP servers (over SSE — Server-Sent Events) and discover what tools they offer
2. **Tool Calling**: When the LLM decides it needs to use a tool (e.g., "schedule an appointment"), the MCP client framework handles the round-trip: sending the tool call request to the MCP server, receiving the result, and feeding it back to the LLM
3. **Agentic Workflows**: This enables **multi-step orchestration** — the LLM can reason about what tool to call, call it, observe the result, and decide what to do next (call another tool, ask for clarification, or give a final answer)

In your resume you mention **appointment scheduling** as an example tool — this would be an MCP server exposing a `scheduleAppointment` tool, and your Spring Boot app (the MCP client) would let the LLM invoke it as part of the conversation.

**Important nuance**: The MCP client dependency is declared and auto-configured, but the current codebase doesn't show explicit MCP server configuration (like SSE endpoint URLs). This suggests it's either configured via environment variables at runtime or is a capability prepared for extension.

## Spring Libraries — How They Fit Together

Here's how the Spring ecosystem pieces snap together like Legos:

```
                    ┌─────────────────────────────┐
                    │   spring-boot-starter-web    │
                    │  (Tomcat + Spring MVC)       │
                    │  @Controller, @GetMapping,   │
                    │  @PostMapping, @RequestBody  │
                    └──────────────┬──────────────┘
                                   │ handles HTTP
                    ┌──────────────▼──────────────┐
                    │ AdoptionsAssistanceController │
                    │  (REST endpoints)            │
                    └──────────────┬──────────────┘
                                   │ uses
                    ┌──────────────▼──────────────┐
                    │   Spring AI ChatClient       │
                    │  .prompt().messages().call() │
                    │  + QuestionAnswerAdvisor     │
                    └───────┬────────────┬────────┘
                            │            │
               ┌────────────▼──┐   ┌─────▼──────────────┐
               │ Bedrock       │   │ PGVector VectorStore│
               │ Converse API  │   │ (spring-ai-starter- │
               │ (Nova Micro)  │   │  vector-store-      │
               │ + MCP Client  │   │  pgvector)          │
               └────────────┬──┘   └─────┬──────────────┘
                            │            │
               ┌────────────▼──┐   ┌─────▼──────────────┐
               │ AWS SDK v2    │   │ spring-boot-starter-│
               │ (Bedrock      │   │ data-jdbc           │
               │  Runtime)     │   │ (CrudRepository,    │
               └───────────────┘   │  DataSource)        │
                                   └─────┬──────────────┘
                                         │
                                   ┌─────▼──────────────┐
                                   │ PostgreSQL 14       │
                                   │ + PGVector extension│
                                   └────────────────────┘
```

Key relationships:
- **`spring-boot-starter-data-jdbc`** provides `CrudRepository<Dog, Integer>` — that's how `DogRepository` works with zero SQL code. Spring generates the `SELECT * FROM dog`, `INSERT INTO dog`, etc. at runtime based on the `Dog` record fields.
- **`spring-ai-starter-vector-store-pgvector`** auto-creates a `VectorStore` bean that knows how to store/query embeddings in PostgreSQL. It uses the same DataSource configured for `spring-boot-starter-data-jdbc`.
- **`spring-ai-starter-model-bedrock-converse`** auto-creates a `ChatClient.Builder` bean that's pre-wired to talk to AWS Bedrock using the Converse API.
- **`spring-ai-advisors-vector-store`** provides `QuestionAnswerAdvisor` — an interceptor pattern that augments prompts with vector search results.

---

# 4. Interview Questions for This Project + Salesforce Position

Given your resume entry and the Salesforce **Agentforce for Supply Chain** role, here are the questions I'd expect, organized by theme.

## Category A: Project Understanding & Architecture

**Q1: Walk me through the end-to-end flow of a user query in your system.**
- *They want to hear*: HTTP request → controller → conversation history → ChatClient → Bedrock Converse API → LLM generates response → store in history → return. Plus the RAG part: how documents were embedded at startup.

**Q2: Why did you choose RAG instead of fine-tuning the model? What are the trade-offs?**
- *They want to hear*: RAG is cheaper, doesn't require retraining, data can be updated in real-time, avoids hallucination for domain-specific data. Fine-tuning is better for learning new styles/behaviors but is expensive and static.

**Q3: How does your system handle conversation context? What are the limitations of your approach?**
- *They want to hear*: `ConcurrentHashMap` for per-user message history, passed as full history to each call. Limitations: in-memory (lost on restart), unbounded growth (no token limit management), doesn't scale horizontally (sessions pinned to one instance).

**Q4: If this system needed to handle 10,000 concurrent users, what would you change?**
- *They want to hear*: Externalize conversation state to Redis or a database, add load balancing, potentially use async/reactive endpoints, implement token windowing or summarization for long conversations, add caching for frequently asked questions.

## Category B: AWS Bedrock & LLM Deep Dive

**Q5: Why Amazon Nova Micro vs other Bedrock models like Claude or Titan? How did you decide?**
- *They want to hear*: Cost/latency trade-offs, Nova Micro is fast and cheap for simple Q&A, Claude is better for complex reasoning, choosing the right model for the use case.

**Q6: Explain the Bedrock Converse API vs the older Invoke API. Why did you use Converse?**
- *They want to hear*: Converse is the unified API that works across all Bedrock models with a consistent interface (messages, system prompts, tool use). Invoke is model-specific and older. Converse supports native tool calling which is essential for agentic workflows.

**Q7: How does Cohere embed-english-v3 create embeddings? What's a vector embedding conceptually?**
- *They want to hear*: Text → neural network → fixed-length float array (1024 dims for Cohere). Semantically similar text produces geometrically close vectors. Cosine similarity measures the angle between vectors.

**Q8: What happens if the Bedrock API is down or rate-limited? How would you handle that?**
- *They want to hear*: Circuit breaker pattern (Resilience4j), exponential backoff, fallback responses, dead letter queues for failed requests, health checks.

## Category C: PGVector & Vector Database

**Q9: Why PGVector over a dedicated vector database like Pinecone or Weaviate?**
- *They want to hear*: Operational simplicity (one database to manage), PostgreSQL is battle-tested, good enough for moderate scale, ACID transactions, can join vector results with relational data. Dedicated vector DBs scale better for billions of vectors.

**Q10: How does vector similarity search work in PGVector? What operators are available?**
- *They want to hear*: `<=>` for cosine distance, `<->` for L2 distance, `<#>` for inner product. PGVector uses IVFFlat or HNSW indexes for approximate nearest neighbor search.

**Q11: What indexing strategies does PGVector support and when would you use each?**
- *They want to hear*: IVFFlat (faster to build, good for static datasets), HNSW (faster queries, more memory, better for dynamic datasets). Without an index, PGVector does exact brute-force search.

## Category D: MCP & Agentic AI (CRITICAL for Salesforce role)

**Q12: Explain the Model Context Protocol. Why is it important for enterprise AI?**
- *They want to hear*: MCP standardizes how LLMs discover and invoke tools. Like a USB-C for AI — any MCP client can talk to any MCP server. For enterprises, this means reusable tools, consistent security, and composable agentic workflows.

**Q13: How does tool-calling work in your system? Walk me through a multi-step agentic workflow.**
- *They want to hear*: LLM receives a prompt + list of available tools → decides to call one → MCP client sends request to MCP server via SSE → receives result → LLM observes result → decides next step (call another tool or respond). Example: "Schedule an adoption appointment" → LLM calls `checkAvailability` tool → gets times → calls `scheduleAppointment` → confirms to user.

**Q14: What's the difference between tool-calling and function-calling? How does SSE (Server-Sent Events) fit into MCP?**
- *They want to hear*: Function-calling is model-provider-specific (OpenAI, Bedrock each have their own format). MCP is a standard protocol that abstracts this. SSE is the transport layer — the MCP server pushes tool responses back to the client as streamed events, enabling real-time, non-blocking communication.

**Q15: How would you ensure reliability and safety in an agentic system where the LLM is making autonomous decisions?**
- *This is HUGE for the Salesforce role.* They want to hear: human-in-the-loop for high-stakes actions, tool permission scoping, input/output validation, guardrails (content filtering), audit logging, rate limiting tool calls, planning verification before execution.

## Category E: Spring Boot & Java (Foundational)

**Q16: Why Spring Data JDBC over JPA/Hibernate?**
- *They want to hear*: JDBC is simpler, no lazy loading surprises, no entity state management, works beautifully with Java records, better for microservices where you want explicit SQL control.

**Q17: What does `@Configuration` + `@Bean` do in your `ChatClientConfig`? How does Spring's dependency injection work here?**
- *They want to hear*: `@Configuration` marks it as a source of bean definitions. `@Bean` methods produce objects managed by Spring's IoC container. Parameters are auto-injected (constructor injection). The `ChatClient.Builder`, `DogRepository`, and `VectorStore` are all auto-configured beans injected by Spring.

**Q18: Your `ConcurrentHashMap` stores conversation history. What thread-safety guarantees does it provide? What doesn't it protect against?**
- *They want to hear*: `ConcurrentHashMap` is thread-safe for put/get. But `computeIfAbsent` + subsequent `messages.add()` is NOT atomic — two concurrent requests for the same user could interleave. You'd need explicit synchronization or an immutable approach.

## Category F: Tailored to Salesforce Agentforce Role

**Q19: How would you apply the architecture you built to a supply chain domain? Say, automating purchase order processing.**
- *They want to hear*: Replace dogs with supply chain entities (POs, invoices, shipments). RAG over logistics documents. MCP tools for ERP integration (SAP, Oracle). Agentic workflow: LLM reads PO → validates against contracts → checks inventory → routes for approval → creates shipment.

**Q20: The job mentions "planning systems that enable agents to execute multi-step workflows autonomously." How does your MCP experience relate?**
- *They want to hear*: MCP + tool-calling is the execution layer. Planning is the reasoning layer — the LLM decomposes a goal into subtasks, decides tool order, handles failures. Your project demonstrates the foundation; Salesforce's Agentforce would add planning, memory, and evaluation layers on top.

**Q21: How would you handle observability for an AI agent in production? How do you debug when the LLM makes a wrong tool call?**
- *They want to hear*: Structured logging of every LLM call (prompt, response, tool calls, latency). Trace IDs across the pipeline. Metrics on tool call success rates, latency percentiles. Splunk/DataDog dashboards. Replay capability — log prompts so you can reproduce issues.

**Q22: The role mentions "safety constraints." How do you prevent an autonomous agent from taking harmful actions in a supply chain context?**
- *They want to hear*: Allowlists for tools, dollar-amount thresholds requiring human approval, read-only vs write tool permissions, sandboxed execution, validation of LLM-generated parameters before tool execution, rollback mechanisms.

---

## Bonus: "Gotcha" Questions They Might Ask

**Q23: I see `QuestionAnswerAdvisor` is instantiated in your controller but never used in the prompt chain. Was that intentional?**
- Honest answer: "It was a work-in-progress. To complete the RAG pipeline, I would add `.advisors(questionAnswerAdvisor)` to the prompt chain. Currently the LLM responds from general knowledge + system prompt, not from retrieved documents at query time."

**Q24: Your Makefile has AWS credentials hardcoded. How would you handle secrets in production?**
- *They want to hear*: AWS IAM roles (instance profiles / ECS task roles), AWS Secrets Manager, environment variables injected by CI/CD, never in code. The Makefile is development-only convenience.

---

## Beginner Q&A: Chat Model vs Embedding Model

### 1) What is the difference between Nova Pro in Bedrock and Cohere embedding model?

In simple words:

- **Nova Pro (chat model)** is like a **writer/conversation assistant**.  
  You give it a question, and it writes a human-style answer.
- **Cohere Embedding model** is like a **meaning converter**.  
  You give it text, and it converts that text into numbers (a vector) so the app can compare meaning and find similar information.

A practical analogy:

- If you run a library:
  - **Embedding model** = librarian indexing every book by topic/meaning.
  - **Chat model** = friendly staff member who talks to visitors and explains answers.

How they work together in your app:

1. Cohere embedding model converts dog descriptions into vectors and stores them in PGVector.
2. When a user asks a question, the system finds the most relevant dog records using vector similarity.
3. Nova Pro (or your configured chat model) then reads that retrieved context and writes the final response.

So:

- **Chat model answers**
- **Embedding model helps the app find the right context to answer from**

### 2) Which library is for chat client and embeddings in this project?

From this project's `pom.xml`:

- **Chat client / Bedrock chat support**
  - `spring-ai-starter-model-bedrock`
  - `spring-ai-starter-model-bedrock-converse`
  - `ChatClient` class (used in Java code)

- **Embeddings + vector database support**
  - `spring-ai-starter-vector-store-pgvector` (stores/searches vectors in PostgreSQL + PGVector)
  - Bedrock Cohere embedding configuration in `application.yml` under:
    - `spring.ai.bedrock.cohere.embedding.model`

- **RAG retrieval helper**
  - `spring-ai-advisors-vector-store`
  - `QuestionAnswerAdvisor` (injects relevant retrieved context into prompts)
