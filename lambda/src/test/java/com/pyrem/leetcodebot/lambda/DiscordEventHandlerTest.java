package com.pyrem.leetcodebot.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscordEventHandlerTest {

    @Mock
    private LambdaClient mockLambdaClient;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger;

    private DiscordEventHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new DiscordEventHandler(objectMapper, mockLambdaClient);

        when(mockContext.getLogger()).thenReturn(mockLogger);
        doNothing().when(mockLogger).log(anyString());
    }

    @Test
    void testHandlePingInteraction() throws Exception {
        // Given
        String pingJson = Files.readString(
            Paths.get("src/test/resources/mock-discord-interaction-ping.json")
        );

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(pingJson);

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"type\":1");
        assertThat(response.getHeaders()).containsEntry("Content-Type", "application/json");

        verify(mockLogger, atLeastOnce()).log(contains("PING"));
        verify(mockLambdaClient, never()).invoke(any(InvokeRequest.class));
    }

    @Test
    void testHandleApplicationCommand() throws Exception {
        // Given
        String commandJson = Files.readString(
            Paths.get("src/test/resources/mock-discord-interaction-command.json")
        );

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(commandJson);

        // Mock Lambda invocation response
        InvokeResponse invokeResponse = InvokeResponse.builder()
            .statusCode(200)
            .build();
        when(mockLambdaClient.invoke(any(InvokeRequest.class))).thenReturn(invokeResponse);

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"type\":5"); // Deferred response
        assertThat(response.getHeaders()).containsEntry("Content-Type", "application/json");

        verify(mockLogger).log(contains("APPLICATION_COMMAND"));
        verify(mockLogger).log(contains("can someone post microsoft questions?"));
        verify(mockLambdaClient).invoke(any(InvokeRequest.class));
    }

    @Test
    void testHandleInvalidJson() {
        // Given
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody("{invalid json");

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(response.getBody()).contains("error");
        verify(mockLogger).log(contains("Error"));
    }

    @Test
    void testHandleUnknownInteractionType() throws Exception {
        // Given
        String unknownTypeJson = "{\"id\":\"123\",\"type\":99,\"version\":1}";

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(unknownTypeJson);

        // When
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mockContext);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Unknown interaction type");
    }
}
