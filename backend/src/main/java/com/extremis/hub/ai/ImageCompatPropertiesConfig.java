package com.extremis.hub.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds extremis.ai.image.* into its own bean. Kept separate from
 * ImageProviderConfig for the same reason AiCompatPropertiesConfig is kept
 * separate from AiProviderConfig: ImageProviderConfig constructor-injects
 * this bean, and defining it as a @Bean method on ImageProviderConfig
 * itself would be circular -- Spring must instantiate that class's
 * constructor before it can call any of its own @Bean methods.
 */
@Configuration
public class ImageCompatPropertiesConfig {

    @Bean
    @ConfigurationProperties(prefix = "extremis.ai.image")
    public OpenAiCompatibleImageProperties imageProperties() {
        return new OpenAiCompatibleImageProperties();
    }
}
