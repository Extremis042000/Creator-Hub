package com.extremis.hub.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same null-bean gate as PaymentProviderConfig: at most one provider
 * bean, chosen by extremis.ai.provider, registered only when AI is
 * enabled AND that provider's own credentials are all present -- so
 * Optional<AiGenerationProvider> is empty otherwise. Deliberately not
 * @ConditionalOnProperty -- that treats an empty-string env var as
 * "present".
 */
@Configuration
@RequiredArgsConstructor
public class AiProviderConfig {

    private final AiProperties properties;
    private final OpenAiCompatibleProperties compatProperties;

    @Bean
    public AiGenerationProvider aiGenerationProvider() {
        if (!properties.isEnabled()) {
            return null;
        }
        return switch (properties.getProvider() == null ? "" : properties.getProvider().trim().toLowerCase()) {
            case "anthropic" -> anthropicProvider();
            case "openai-compatible" -> openAiCompatibleProvider();
            default -> null;
        };
    }

    private AiGenerationProvider anthropicProvider() {
        if (isBlank(properties.getApiKey())) {
            return null;
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(properties.getApiKey())
            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .maxRetries(properties.getMaxRetries())
            .build();
        return new ClaudeGenerationProvider(client, properties);
    }

    private AiGenerationProvider openAiCompatibleProvider() {
        if (isBlank(compatProperties.getBaseUrl())
                || isBlank(compatProperties.getApiKey())
                || isBlank(compatProperties.getModel())) {
            return null;
        }
        return new OpenAiCompatibleGenerationProvider(compatProperties);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
