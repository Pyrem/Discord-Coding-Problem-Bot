package com.pyrem.leetcodebot.lambda.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Discord Interaction payload
 * See: https://discord.com/developers/docs/interactions/receiving-and-responding
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscordInteraction {

    @JsonProperty("id")
    private String id;

    @JsonProperty("application_id")
    private String applicationId;

    @JsonProperty("type")
    private int type; // 1 = PING, 2 = APPLICATION_COMMAND

    @JsonProperty("data")
    private InteractionData data;

    @JsonProperty("channel_id")
    private String channelId;

    @JsonProperty("token")
    private String token;

    public DiscordInteraction() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public InteractionData getData() { return data; }
    public void setData(InteractionData data) { this.data = data; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InteractionData {
        @JsonProperty("name")
        private String name;

        @JsonProperty("options")
        private InteractionOption[] options;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public InteractionOption[] getOptions() { return options; }
        public void setOptions(InteractionOption[] options) { this.options = options; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InteractionOption {
        @JsonProperty("name")
        private String name;

        @JsonProperty("value")
        private String value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
