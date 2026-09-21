package com.extremis.hub.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same null-bean gate as PaymentProviderConfig: registers the provider
 * only when AI generation is enabled AND a real key is present, so
 * Optional<AiGenerationProvider> is empty otherwise. Deliberately not
 * @ConditionalOnProperty -- that treats an empty-string env var as
 * "present".
 */
@Configuration
@RequiredArgsConstructor
public class AiProviderConfig {

    private final AiProperties properties;

    @Bean
    public AiGenerationProvider aiGenerationProvider() {
        if (!properties.isEnabled() || isBlank(properties.getApiKey())) {
            return null;
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
            .apiKey(properties.getApiKey())
            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
            .maxRetries(properties.getMaxRetries())
            .build();
        return new ClaudeGenerationProvider(client, properties);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
