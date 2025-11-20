package com.pyrem.leetcodebot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring AI components
 */
@Configuration
public class SpringAiConfig {

    /**
     * Configure ChatClient.Builder for use throughout the application
     * Spring AI auto-configuration provides the ChatModel bean
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(org.springframework.ai.chat.model.ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}
