package com.pyrem.leetcodebot.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyrem.leetcodebot.lambda.model.CompanyProblemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestParserFunctionTest {

    @Mock
    private BedrockRuntimeClient mockBedrockClient;

    @Mock
    private LambdaClient mockLambdaClient;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger;

    private RequestParserFunction handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new RequestParserFunction(objectMapper, mockBedrockClient, mockLambdaClient);

        when(mockContext.getLogger()).thenReturn(mockLogger);
        doNothing().when(mockLogger).log(anyString());
    }

    @Test
    void testParseBedrockResponse() throws Exception {
        // Given
        String bedrockResponseJson = Files.readString(
            Paths.get("src/test/resources/mock-bedrock-response.json")
        );

        // When
        CompanyProblemRequest result = handler.parseBedrockResponse(bedrockResponseJson);

        // Then
        assertThat(result.getCompanies()).containsExactly("Microsoft");
        assertThat(result.getTimeRange()).isNull();
        assertThat(result.isExplicitTimeRange()).isFalse();
    }

    @Test
    void testHandleRequest() throws Exception {
        // Given
        Map<String, Object> event = new HashMap<>();
        event.put("message", "can someone post microsoft questions?");
        event.put("interaction", Map.of("id", "123", "token", "abc"));

        // Mock Bedrock response
        String bedrockResponseJson = Files.readString(
            Paths.get("src/test/resources/mock-bedrock-response.json")
        );

        InvokeModelResponse bedrockResponse = InvokeModelResponse.builder()
            .body(SdkBytes.fromUtf8String(bedrockResponseJson))
            .build();

        when(mockBedrockClient.invokeModel(any(InvokeModelRequest.class)))
            .thenReturn(bedrockResponse);

        // Mock Lambda invocation
        InvokeResponse invokeResponse = InvokeResponse.builder()
            .statusCode(200)
            .build();
        when(mockLambdaClient.invoke(any(InvokeRequest.class)))
            .thenReturn(invokeResponse);

        // When
        CompanyProblemRequest result = handler.handleRequest(event, mockContext);

        // Then
        assertThat(result.getCompanies()).containsExactly("Microsoft");
        assertThat(result.getTimeRange()).isNull();

        verify(mockLogger).log(contains("Parsing message"));
        verify(mockLogger).log(contains("Calling Bedrock"));
        verify(mockLogger).log(contains("Parsed companies"));
        verify(mockBedrockClient).invokeModel(any(InvokeModelRequest.class));
        verify(mockLambdaClient).invoke(any(InvokeRequest.class));
    }

    @Test
    void testParseWithExplicitTimeRange() throws Exception {
        // Given
        String bedrockResponseWithTimeRange = """
            {
              "content": [
                {
                  "text": "{\\"companies\\":[\\"Google\\"],\\"timeRange\\":\\"last30days\\",\\"explicitTimeRange\\":true}"
                }
              ],
              "usage": {"input_tokens": 250, "output_tokens": 50}
            }
            """;

        // When
        CompanyProblemRequest result = handler.parseBedrockResponse(bedrockResponseWithTimeRange);

        // Then
        assertThat(result.getCompanies()).containsExactly("Google");
        assertThat(result.getTimeRange()).isEqualTo("last30days");
        assertThat(result.isExplicitTimeRange()).isTrue();
    }

    @Test
    void testParseMultipleCompanies() throws Exception {
        // Given
        String bedrockResponseMultiple = """
            {
              "content": [
                {
                  "text": "{\\"companies\\":[\\"Microsoft\\",\\"Google\\",\\"Amazon\\"],\\"timeRange\\":null,\\"explicitTimeRange\\":false}"
                }
              ],
              "usage": {"input_tokens": 250, "output_tokens": 50}
            }
            """;

        // When
        CompanyProblemRequest result = handler.parseBedrockResponse(bedrockResponseMultiple);

        // Then
        assertThat(result.getCompanies()).containsExactly("Microsoft", "Google", "Amazon");
        assertThat(result.getTimeRange()).isNull();
        assertThat(result.isExplicitTimeRange()).isFalse();
    }
}
