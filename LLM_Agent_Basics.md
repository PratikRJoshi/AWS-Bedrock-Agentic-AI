# LLMs and Agents Learning

Agentic AI

1. What is RAG?
2. What does performing RAG over something (eg. file) mean ?

You're asking about **RAG (Retrieval-Augmented Generation)**, a fundamental concept in building modern, grounded Agentic AI systems.

## 1. What is RAG?

**RAG (Retrieval-Augmented Generation)** is an architecture or technique used to enhance the capability and reliability of Large Language Models (LLMs) by giving them access to external, up-to-date, or proprietary knowledge *before* they generate a response.

Essentially, RAG solves the problem of LLMs being limited to the knowledge they were trained on (which is static and often outdated).

### Analogy

Think of an LLM without RAG as a student taking an exam purely from memory. An LLM with RAG is that same student, but allowed to **search a specific, relevant textbook or document library** before answering each question. The search results (the "retrieved" documents) are then included in the prompt, giving the model the context it needs to generate a factually accurate and current answer.

***

## 2. What does performing RAG over something (e.g., a file) mean?

Performing RAG over a specific knowledge source (like a file, database, or internal documentation) means **you have indexed that source and made it searchable in a format an LLM can use.**

It involves a three-step process:

### Step 1: Indexing (The "Retrieval" Setup)

1.  **Chunking:** The source (e.g., your PDF file) is broken down into small, manageable pieces of text called "chunks."
2.  **Embedding:** Each text chunk is converted into a list of numbers called a **vector** (or embedding). These vectors capture the semantic meaning of the text.
3.  **Storage:** These vectors are stored in a special database called a **vector database**.

### Step 2: Retrieval (The "Augmentation" Phase)

1.  When a user asks a question, the question itself is also converted into a vector (query embedding).
2.  The system uses this query vector to search the vector database (which holds the file's indexed chunks).
3.  It finds the **top $K$ most relevant text chunks** (the closest matching vectors) from your file.

### Step 3: Generation (The "Generation" Phase)

1.  The system combines the original user query and the retrieved text chunks from the file into a single, comprehensive **prompt**.
2.  This new, augmented prompt is sent to the LLM.
3.  The LLM uses the relevant content of your file (the retrieved chunks) as its sole source of truth to **generate the final, grounded answer**.

**In short, performing RAG over a file means transforming that file into a searchable library that an AI can reference in real-time to answer questions.**