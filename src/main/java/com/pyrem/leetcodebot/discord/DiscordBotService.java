package com.pyrem.leetcodebot.discord;

import com.pyrem.leetcodebot.model.CompanyProblemRequest;
import com.pyrem.leetcodebot.model.LeetCodeProblem;
import com.pyrem.leetcodebot.nlp.RequestParserService;
import com.pyrem.leetcodebot.service.LeetCodeService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Discord bot service using JDA
 * Listens to messages, parses requests, and responds with LeetCode problem sets
 */
@Service
@Slf4j
public class DiscordBotService extends ListenerAdapter {

    private final RequestParserService requestParserService;
    private final LeetCodeService leetCodeService;

    @Value("${discord.bot.token}")
    private String botToken;

    @Value("${discord.bot.command.prefix:!}")
    private String commandPrefix;

    private JDA jda;

    public DiscordBotService(RequestParserService requestParserService, LeetCodeService leetCodeService) {
        this.requestParserService = requestParserService;
        this.leetCodeService = leetCodeService;
    }

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Discord bot...");

            jda = JDABuilder.createDefault(botToken)
                .enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.DIRECT_MESSAGES
                )
                .addEventListeners(this)
                .build()
                .awaitReady();

            log.info("Discord bot initialized successfully! Logged in as: {}", jda.getSelfUser().getName());
        } catch (Exception e) {
            log.error("Failed to initialize Discord bot: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (jda != null) {
            log.info("Shutting down Discord bot...");
            jda.shutdown();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore messages from bots (including ourselves)
        if (event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentRaw().trim();

        // Ignore empty messages
        if (content.isEmpty()) {
            return;
        }

        // Check if message is a potential request (contains company name or question mark)
        if (!isPotentialRequest(content)) {
            return;
        }

        log.info("Received potential request: {} from user: {}",
            content, event.getAuthor().getName());

        try {
            // Parse the request using NLP
            CompanyProblemRequest request = requestParserService.parseRequest(content);

            // Validate request
            if (request.getCompanies() == null || request.getCompanies().isEmpty()) {
                log.debug("No companies found in message, ignoring: {}", content);
                return;
            }

            // Send typing indicator
            event.getChannel().sendTyping().queue();

            // Process each company
            for (String company : request.getCompanies()) {
                List<LeetCodeProblem> problems = leetCodeService.getProblems(
                    company,
                    request.getTimeRange(),
                    request.isExplicitTimeRange()
                );

                // Send response
                sendProblemListResponse(event.getChannel(), company, problems);
            }

        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            event.getChannel().sendMessage("❌ Sorry, I encountered an error processing your request. Please try again.")
                .queue();
        }
    }

    /**
     * Check if message is a potential LeetCode request
     */
    private boolean isPotentialRequest(String content) {
        String lower = content.toLowerCase();

        // Check for question mark (simple request like "Microsoft?")
        if (content.contains("?")) {
            return true;
        }

        // Check for keywords
        return lower.contains("leetcode") ||
               lower.contains("problem") ||
               lower.contains("question") ||
               lower.contains("microsoft") ||
               lower.contains("google") ||
               lower.contains("amazon") ||
               lower.contains("meta") ||
               lower.contains("facebook") ||
               lower.contains("apple");
    }

    /**
     * Send problem list as rich embeds (Discord has a limit of 10 embeds per message)
     */
    private void sendProblemListResponse(MessageChannel channel, String company, List<LeetCodeProblem> problems) {
        if (problems.isEmpty()) {
            channel.sendMessage(String.format("No problems found for **%s** 😕", company)).queue();
            return;
        }

        // Create header embed
        EmbedBuilder headerEmbed = new EmbedBuilder()
            .setTitle(String.format("📋 %s LeetCode Problems", company))
            .setDescription(String.format("Found **%d** problems", problems.size()))
            .setColor(Color.decode("#FFA116")); // LeetCode orange color

        channel.sendMessageEmbeds(headerEmbed.build()).queue();

        // Send problems in batches (Discord allows max 10 embeds per message)
        List<MessageEmbed> embeds = new ArrayList<>();

        for (LeetCodeProblem problem : problems) {
            embeds.add(createProblemEmbed(problem));

            // Send batch of 10 embeds
            if (embeds.size() == 10) {
                channel.sendMessageEmbeds(embeds).queue();
                embeds.clear();
            }
        }

        // Send remaining embeds
        if (!embeds.isEmpty()) {
            channel.sendMessageEmbeds(embeds).queue();
        }
    }

    /**
     * Create a rich embed for a single problem (similar to the screenshot)
     */
    private MessageEmbed createProblemEmbed(LeetCodeProblem problem) {
        EmbedBuilder embed = new EmbedBuilder();

        // Title with problem number and name
        String title = String.format("%d. %s", problem.getProblemNumber(), problem.getProblemName());
        embed.setTitle(title, problem.getUrl());

        // Set color based on difficulty
        Color color = switch (problem.getDifficulty()) {
            case EASY -> Color.decode("#00B8A3"); // Green
            case MEDIUM -> Color.decode("#FFC01E"); // Yellow/Orange
            case HARD -> Color.decode("#EF4743"); // Red
        };
        embed.setColor(color);

        // Add fields for problem details
        StringBuilder details = new StringBuilder();

        // Acceptance rate
        String acceptancePercent = String.format("%.1f%%", problem.getAcceptanceRate() * 100);
        details.append("**Acceptance:** ").append(acceptancePercent).append("\n");

        // Difficulty
        details.append("**Difficulty:** ").append(problem.getDifficulty().getDisplayName()).append("\n");

        // Frequency bar (visual representation using Unicode blocks)
        String frequencyBar = createFrequencyBar(problem.getFrequency());
        details.append("**Frequency:** ").append(frequencyBar);

        embed.setDescription(details.toString());

        return embed.build();
    }

    /**
     * Create a visual frequency bar using Unicode characters
     * Similar to the bars shown in the screenshot
     */
    private String createFrequencyBar(Double frequency) {
        if (frequency == null) {
            frequency = 0.0;
        }

        // Normalize frequency to 0-10 scale
        int barLength = (int) Math.round(frequency * 10);
        barLength = Math.max(0, Math.min(10, barLength)); // Clamp to 0-10

        StringBuilder bar = new StringBuilder();

        // Use block characters to create visual bar
        for (int i = 0; i < 10; i++) {
            if (i < barLength) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        // Add percentage
        String percentStr = String.format(" %.0f%%", frequency * 100);
        bar.append(percentStr);

        return bar.toString();
    }
}
