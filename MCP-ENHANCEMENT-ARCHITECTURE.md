# MCP Enhancement Architecture

This document explains how MCP extends the current RAG implementation.

## Current State

- The app already performs retrieval with `QuestionAnswerAdvisor` and PGVector.
- The app can answer dog-adoption questions from indexed dog descriptions.
- Business actions (for example, scheduling pickup appointments) are not exposed as reusable remote tools.

## Target State with MCP

- Keep RAG for factual retrieval from your dog dataset.
- Add a separate MCP server that exposes action-oriented tools (starting with scheduling).
- Configure the current app as an MCP client over SSE.
- Let the model decide when to call MCP tools, then continue response generation with tool results.

## Request Flow

1. User asks a question.
2. Chat request goes to the current app.
3. `QuestionAnswerAdvisor` retrieves relevant dog context from PGVector.
4. Model generates response and may choose a tool call.
5. MCP client sends tool invocation to remote MCP server over SSE.
6. MCP server executes tool (for example, scheduling logic) and returns structured result.
7. Model incorporates tool output and returns final answer.

## Why This Improves the Design

- Reusability: tool logic is centralized and can be used by multiple AI apps.
- Separation of concerns: current app focuses on conversation + retrieval; MCP server focuses on business actions.
- Extensibility: new tools can be added server-side without copying logic into each AI client app.
- Enterprise fit: remote tooling over SSE aligns with distributed deployment models.
