package com.extremis.hub.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 40: same null-bean gate as AiProviderConfig -- Optional<ImageGenerationProvider>
 * stays empty unless extremis.ai.image.enabled is true AND
 * generations-url/api-key/model are all non-blank. Deliberately not
 * @ConditionalOnProperty (it treats an empty-string env var as "present").
 * Only one backend today -- no round-robin/composite like the text path;
 * add one only if a second real image gateway is actually found.
 */
@Configuration
@RequiredArgsConstructor
public class ImageProviderConfig {

    private final OpenAiCompatibleImageProperties properties;

    @Bean
    public ImageGenerationProvider imageGenerationProvider() {
        if (!properties.isEnabled() || isBlank(properties.getGenerationsUrl())
                || isBlank(properties.getApiKey()) || isBlank(properties.getModel())) {
            return null;
        }
        return new OpenAiCompatibleImageProvider(properties);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
