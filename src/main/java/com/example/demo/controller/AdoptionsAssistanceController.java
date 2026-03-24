package com.example.demo.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API CONTROLLER — the HTTP entry point for all user interactions with the AI.
 *
 * @Controller marks this class as a Spring MVC controller (eligible for request mapping).
 * @ResponseBody tells Spring to write the return value directly into the HTTP response body
 * as plain text (not resolve it as a view/template name).
 *
 * WHY @Controller + @ResponseBody instead of @RestController?
 *   — They are functionally identical. @RestController = @Controller + @ResponseBody.
 *     Using them separately is an older style; either works. @RestController is more concise
 *     and is the modern convention for REST APIs.
 *
 * WHY a controller layer at all instead of putting AI logic in a @Service?
 *   — Separation of concerns: the controller handles HTTP (request parsing, response formatting),
 *     while a service layer would handle business logic. For this small app, they're combined,
 *     but in production you'd extract AI orchestration into an @Service for testability.
 */
@Controller
@ResponseBody
public class AdoptionsAssistanceController {

    /**
     * The ChatClient bean — our gateway to AWS Bedrock's LLM.
     * Injected by Spring from the bean created in ChatClientConfig.
     */
    private final ChatClient chatClient;

    /**
     * The Bedrock model ID (e.g., "amazon.nova-micro-v1:0") pulled from application.yml.
     *
     * WHY inject via @Value instead of hardcoding?
     *   — Externalizing config means we can switch models (Nova → Claude → Titan)
     *     by changing an env var or YAML, without recompiling code.
     *   — Different environments (dev/staging/prod) can use different models.
     */
    private final String modelId;

    /**
     * In-memory store of per-user conversation history.
     *
     * WHY ConcurrentHashMap instead of HashMap?
     *   — Thread safety: a web server handles multiple requests concurrently on different
     *     threads. HashMap is NOT thread-safe — concurrent reads/writes can corrupt it.
     *     ConcurrentHashMap uses fine-grained locking (segment-level) for safe concurrent access.
     *
     * WHY in-memory Map instead of a database or Redis?
     *   — Simplicity for a demo. Trade-off: conversation history is lost on app restart,
     *     and it doesn't scale horizontally (each server instance has its own map).
     *   — In production, you'd use Redis (fast, shared across instances) or a database
     *     (durable, queryable) to persist conversation state.
     *
     * LIMITATION: While ConcurrentHashMap.computeIfAbsent() is atomic, the subsequent
     * messages.add() on the returned ArrayList is NOT synchronized. Two concurrent requests
     * for the same user could interleave message additions. A production fix would be
     * Collections.synchronizedList() or explicit locking per user key.
     */
    private final Map<String, List<Message>> conversationHistory;

    /**
     * Constructor injection — Spring automatically provides both parameters.
     *
     * WHY constructor injection instead of @Autowired on fields?
     *   — Immutability: fields can be 'final', guaranteeing they're set once and never null.
     *   — Testability: in unit tests you can pass mocks directly via the constructor
     *     without needing Spring's DI container or reflection.
     *   — It's the Spring team's officially recommended approach.
     */
    AdoptionsAssistanceController(ChatClient chatClient,
                                   @Value("${spring.ai.bedrock.converse.chat.model}") String modelId) {
        this.chatClient = chatClient;
        this.modelId = modelId;
        this.conversationHistory = new ConcurrentHashMap<>();
    }

    /**
     * GET /{user}/inquire?question=...
     *
     * A per-user conversational endpoint. The {user} path variable isolates each user's
     * conversation history, enabling multi-turn dialogue (the LLM sees all prior messages).
     *
     * FLOW:
     *   1. Look up (or create) the user's message history list
     *   2. Wrap the question in a UserMessage and append to history
     *   3. Send the FULL history to Bedrock — this is how the LLM "remembers" context
     *   4. Receive the LLM's response, wrap in AssistantMessage, append to history
     *   5. Return the response as plain text
     *
     * WHY send the full history each time instead of just the latest question?
     *   — LLMs are stateless — they don't remember previous calls. To maintain conversation
     *     context, we must resend the entire dialogue each time. The model reads all messages
     *     and generates a contextually aware response.
     *   — Trade-off: as conversations grow, token usage increases. A production system would
     *     implement windowing (keep last N messages) or summarization to manage token limits.
     *
     * WHY @GetMapping instead of @PostMapping for a chat endpoint?
     *   — This is a design choice for easy browser-based testing (you can just type the URL).
     *     Strictly speaking, POST is more appropriate for actions that create state (a new message).
     *     GET requests can also leak the question in server logs and URL history.
     *
     * WHY ChatOptions.builder().model(modelId) at call time?
     *   — This allows per-request model override. The ChatClient may have a default model,
     *     but specifying it here gives explicit control and makes the code self-documenting
     *     about which model is being used for this specific endpoint.
     */
    @GetMapping("/{user}/inquire")
    public String inquire(@PathVariable("user") String user,
                            @RequestParam String question) {
        // computeIfAbsent: if this user has no history yet, create a new ArrayList.
        // This is atomic within ConcurrentHashMap — no race condition on the map itself.
        List<Message> messages = this.conversationHistory.computeIfAbsent(
            user, k -> new ArrayList<>()
        );

        // Wrap the raw string question into Spring AI's UserMessage type.
        // Spring AI uses a Message interface hierarchy (UserMessage, AssistantMessage,
        // SystemMessage) to structure the conversation for the Bedrock Converse API,
        // which expects messages with explicit roles ("user", "assistant", "system").
        UserMessage userMessage = new UserMessage(question);
        messages.add(userMessage);

        // Build and execute the LLM call:
        //   .prompt()    — start building a new prompt request
        //   .messages()  — attach the full conversation history (provides context)
        //   .options()   — set model-specific options (which Bedrock model to invoke)
        //   .call()      — synchronously send to AWS Bedrock and wait for the response
        //   .content()   — extract just the text content from the response object
        //
        // WHY .call() (synchronous) instead of .stream() (streaming)?
        //   — Simplicity for a demo. .stream() would return a Flux<String> for
        //     real-time token-by-token streaming (better UX for long responses),
        //     but requires reactive/SSE endpoint setup.
        String response = this.chatClient
            .prompt()
            .messages(messages)
            .options(ChatOptions.builder()
                    .model(modelId)
                    .build())
            .call()
            .content();

        // Store the assistant's response in history so future calls include it as context.
        // This enables multi-turn conversations like:
        //   User: "Show me friendly dogs"  → Assistant: "Here's Buddy and Bella..."
        //   User: "Tell me more about the first one" → Assistant knows "first one" = Buddy
        AssistantMessage assistantMessage = new AssistantMessage(response);
        messages.add(assistantMessage);

        return response;
    }
}
