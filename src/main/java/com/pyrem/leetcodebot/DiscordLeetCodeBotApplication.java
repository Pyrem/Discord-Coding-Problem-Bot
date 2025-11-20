package com.pyrem.leetcodebot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Main Spring Boot application class for Discord LeetCode Bot
 */
@SpringBootApplication
@Slf4j
public class DiscordLeetCodeBotApplication {

    public static void main(String[] args) {
        log.info("Starting Discord LeetCode Bot Application...");
        SpringApplication.run(DiscordLeetCodeBotApplication.class, args);
        log.info("Discord LeetCode Bot Application started successfully!");
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
