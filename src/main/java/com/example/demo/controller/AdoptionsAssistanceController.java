package com.example.demo.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ResponseBody
public class AdoptionsAssistanceController {
    private final ChatClient chatClient;
    private final String modelId;

    AdoptionsAssistanceController(ChatClient chatClient,
                                   @Value("${spring.ai.bedrock.converse.chat.model}") String modelId) {
        this.chatClient = chatClient;
        this.modelId = modelId;
    }

    @GetMapping("/{user}/inquire")
    public String inquire(@PathVariable("user") String user,
                            @RequestParam String question) {
        return this.chatClient
                .prompt()
                .user(question)
                .options(ChatOptions.builder()
                        .model(modelId)
                        .build())
                .call()
                .content();
    }
}
