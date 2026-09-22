package com.extremis.hub.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the SAME OpenAiCompatibleProperties shape at two different
 * prefixes, so a second openai-compatible backend is config-only (env
 * vars), never a new class. Deliberately its own @Configuration class
 * with no constructor dependencies: these two beans are constructor-
 * injected into AiProviderConfig, and defining them as @Bean methods
 * on AiProviderConfig itself would be circular -- Spring must
 * instantiate that class's constructor before it can call any of its
 * own @Bean methods.
 */
@Configuration
public class AiCompatPropertiesConfig {

    @Bean
    @ConfigurationProperties(prefix = "extremis.ai.compat")
    public OpenAiCompatibleProperties primaryCompatProperties() {
        return new OpenAiCompatibleProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "extremis.ai.compat2")
    public OpenAiCompatibleProperties secondaryCompatProperties() {
        return new OpenAiCompatibleProperties();
    }
}
