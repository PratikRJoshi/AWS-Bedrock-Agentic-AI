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

@Controller
@ResponseBody
public class AdoptionsAssistanceController {
    private final ChatClient chatClient;
    private final String modelId;

    // Store conversation history for each user
    private final Map<String, List<Message>> conversationHistory;

    AdoptionsAssistanceController(ChatClient chatClient,
                                   @Value("${spring.ai.bedrock.converse.chat.model}") String modelId) {
        this.chatClient = chatClient;
        this.modelId = modelId;
        this.conversationHistory = new ConcurrentHashMap<>();
    }

    @GetMapping("/{user}/inquire")
    public String inquire(@PathVariable("user") String user,
                            @RequestParam String question) {
        // Get or create conversation history for the user
        List<Message> messages = this.conversationHistory.computeIfAbsent(
            user, k -> new ArrayList<>()
        );

        // Add the user's question to the conversation history
        UserMessage userMessage = new UserMessage(question);
        messages.add(userMessage);

        // Get the AI's response using the conversation history
        String response = this.chatClient
            .prompt()
            .messages(messages)
            .options(ChatOptions.builder()
                    .model(modelId)
                    .build())
            .call()
            .content();

        // Add the AI's response to the conversation history
        AssistantMessage assistantMessage = new AssistantMessage(response);
        messages.add(assistantMessage);

        return response;
    }
}
