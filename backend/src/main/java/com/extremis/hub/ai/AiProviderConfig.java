package com.extremis.hub.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
 *
 * The openai-compatible path supports up to two backends (FreeModel,
 * Free.ai), bound from the SAME OpenAiCompatibleProperties class at two
 * different prefixes -- see AiCompatPropertiesConfig, injected here by
 * bean name (primaryCompatProperties/secondaryCompatProperties) since
 * both beans share a type. Adding a backend is config-only (env vars);
 * either, both, or neither may be set. Two configured -> round-robin
 * between them (CompositeAiGenerationProvider); one -> that one
 * directly, no wrapping.
 */
@Configuration
@RequiredArgsConstructor
public class AiProviderConfig {

    private final AiProperties properties;
    private final OpenAiCompatibleProperties primaryCompatProperties;
    private final OpenAiCompatibleProperties secondaryCompatProperties;

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
        List<AiGenerationProvider> configured = new ArrayList<>();
        if (isConfigured(primaryCompatProperties)) {
            configured.add(new OpenAiCompatibleGenerationProvider(primaryCompatProperties));
        }
        if (isConfigured(secondaryCompatProperties)) {
            configured.add(new OpenAiCompatibleGenerationProvider(secondaryCompatProperties));
        }
        if (configured.isEmpty()) {
            return null;
        }
        return configured.size() == 1 ? configured.get(0) : new CompositeAiGenerationProvider(configured);
    }

    private boolean isConfigured(OpenAiCompatibleProperties compat) {
        return !isBlank(compat.getChatUrl()) && !isBlank(compat.getApiKey()) && !isBlank(compat.getModel());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
