package com.example.demo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SPRING AI CHAT CLIENT CONFIGURATION
 *
 * This class is responsible for creating the ChatClient bean — the central object
 * that our controller uses to send prompts to AWS Bedrock and receive LLM responses.
 *
 * @Configuration tells Spring: "this class contains @Bean factory methods — scan it at
 * startup and register whatever the @Bean methods return into the IoC container."
 *
 * WHY a separate @Configuration class instead of putting @Bean in the main app class?
 *   — Separation of concerns: config classes group related bean definitions together.
 *     As the app grows (adding RAG advisors, system prompts, vector store wiring),
 *     this file becomes the single place to modify AI behavior without touching controllers.
 *   — Testability: you can swap this config with a test-specific one that returns a mock ChatClient.
 */
@Configuration
public class ChatClientConfig {

    /**
     * Creates and registers the ChatClient bean in Spring's IoC container.
     *
     * HOW THIS WORKS:
     *   — Spring Boot auto-configuration (from spring-ai-starter-model-bedrock-converse)
     *     automatically creates a ChatClient.Builder bean that is pre-wired to talk to
     *     AWS Bedrock using the model + credentials defined in application.yml.
     *   — This method receives that builder via constructor injection (Spring sees the
     *     parameter type, finds a matching bean, and passes it in).
     *   — We call builder.build() to finalize the ChatClient.
     *
     * WHY ChatClient.Builder pattern instead of "new ChatClient(...)"?
     *   — The Builder pattern lets Spring AI pre-configure complex internals
     *     (HTTP clients, auth, serialization) while giving us a hook to customize
     *     (e.g., adding .defaultSystem() for a system prompt, .defaultAdvisors() for RAG).
     *   — Currently this is a minimal build(). To add RAG, you would do:
     *       builder.defaultSystem("You are a dog adoption assistant...")
     *              .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
     *              .build();
     *
     * WHY Spring AI's ChatClient instead of calling AWS SDK's BedrockRuntimeClient directly?
     *   — ChatClient provides a fluent, model-agnostic API: .prompt().messages().call().content()
     *     works the same whether the backend is Bedrock, OpenAI, or Ollama.
     *   — It handles message serialization, retry logic, and the Converse API protocol.
     *   — Switching from Nova to Claude is a one-line YAML change, not a code rewrite.
     *
     * @param builder auto-configured by spring-ai-starter-model-bedrock-converse
     * @return a ready-to-use ChatClient wired to AWS Bedrock
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
